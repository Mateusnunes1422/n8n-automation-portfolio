package br.farol.corrida

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * Lê o texto da oferta na tela do app de corrida e mostra o semáforo por cima.
 *
 * Não toca em nada: a sobreposição é declarada como NOT_TOUCHABLE, então nunca
 * bloqueia o botão de aceitar. Quem decide é o motorista.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    private var cfg = Settings()
    private var lastSignature: String? = null
    private var lastSeenAt = 0L
    private var pendingScan: Runnable? = null

    companion object {
        private const val DEBOUNCE_MS = 250L
        private const val HIDE_AFTER_MS = 2500L
        private const val MAX_NODES = 400
    }

    /**
     * Recarrega as metas só quando o motorista muda alguma coisa na tela de
     * configuração. Ler as preferências a cada evento de tela seria desperdício:
     * o Android dispara dezenas deles por segundo.
     */
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != "lastCapture" && key != "lastCapturePkg") cfg = Settings.load(this)
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        cfg = Settings.load(this)
        Settings.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!cfg.enabled) { hideOverlay(); return }

        val pkg = event.packageName?.toString() ?: return
        val monitored = pkg in cfg.monitoredPackages

        // Fora do modo aprendizado só olhamos os apps de corrida conhecidos.
        // Com ele ligado olhamos qualquer tela, para descobrir o nome do pacote
        // quando o Uber ou a 99 aparecem com um pacote que ainda não está na lista.
        if (!monitored && !cfg.learnMode) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scheduleScan(pkg, monitored)
        }
    }

    private fun scheduleScan(pkg: String, monitored: Boolean) {
        pendingScan?.let { handler.removeCallbacks(it) }
        val r = Runnable { scan(pkg, monitored) }
        pendingScan = r
        handler.postDelayed(r, DEBOUNCE_MS)
    }

    private fun scan(pkg: String, monitored: Boolean) {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectText(root, texts, 0)
        if (texts.isEmpty()) return

        if (cfg.learnMode) {
            val joined = texts.joinToString("\n")
            // Numa tela de app desconhecido, só guarda se parecer uma oferta.
            // Evita registrar tela de banco, conversa e afins enquanto diagnostica.
            if (monitored || joined.contains("R$")) {
                Settings.saveLastCapture(this, pkg, joined)
            }
        }

        // Card só aparece nos apps de corrida conhecidos.
        if (!monitored) return

        val offer = OfferParser.parse(texts)
        val now = System.currentTimeMillis()

        if (!offer.isUsable) {
            // A oferta sumiu da tela — esconde depois de um instante.
            if (now - lastSeenAt > HIDE_AFTER_MS) {
                hideOverlay()
                lastSignature = null
            }
            return
        }

        lastSeenAt = now
        if (offer.signature == lastSignature) return   // mesma oferta, já mostrada
        lastSignature = offer.signature

        val verdict = OfferEvaluator.evaluate(offer, cfg)
        showOverlay(offer, verdict)
    }

    /** Percorre a árvore de nós juntando todo texto visível. */
    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > MAX_NODES) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1)
    }

    // ---------------- Sobreposição ----------------

    private fun showOverlay(offer: Offer, v: Verdict) {
        if (!AndroidSettings.canDrawOverlays(this)) return
        val wm = windowManager ?: return

        if (overlay == null) {
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_card, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // Nunca rouba o toque: o botão de aceitar continua funcionando.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = 40
            }
            try {
                wm.addView(view, params)
                overlay = view
            } catch (e: Exception) {
                return
            }
        }

        val view = overlay ?: return
        val bg = when (v.level) {
            Level.GREEN -> R.drawable.bg_verdict_green
            Level.YELLOW -> R.drawable.bg_verdict_yellow
            Level.RED -> R.drawable.bg_verdict_red
        }
        view.findViewById<View>(R.id.card).setBackgroundResource(bg)

        view.findViewById<TextView>(R.id.verdict).text = when (v.level) {
            Level.GREEN -> "ACEITAR"
            Level.YELLOW -> "AVALIAR"
            Level.RED -> "RECUSAR"
        }

        view.findViewById<TextView>(R.id.perKm).text =
            v.rsPerKm?.let { "R$ %.2f/km".format(it).replace(".", ",") } ?: "—"

        val perHour = v.rsPerHour?.let { "R$ %.0f/h".format(it) } ?: "—"
        val dist = "%.1f km".format(v.totalKm).replace(".", ",")
        view.findViewById<TextView>(R.id.details).text =
            "$perHour  ·  $dist  ·  ${v.totalMin} min  ·  ${OfferEvaluator.fmt(offer.fare ?: 0.0)}"

        val profitView = view.findViewById<TextView>(R.id.profit)
        if (v.netProfit != null) {
            profitView.visibility = View.VISIBLE
            profitView.text = "Líquido s/ combustível ≈ ${OfferEvaluator.fmt(v.netProfit)}"
        } else profitView.visibility = View.GONE

        val alertView = view.findViewById<TextView>(R.id.alert)
        if (v.alerts.isNotEmpty()) {
            alertView.visibility = View.VISIBLE
            alertView.text = "⚠ " + v.alerts.joinToString(" · ")
        } else alertView.visibility = View.GONE

        view.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        overlay?.let { it.visibility = View.GONE }
    }

    private fun removeOverlay() {
        overlay?.let { v -> try { windowManager?.removeView(v) } catch (_: Exception) {} }
        overlay = null
    }

    override fun onInterrupt() { hideOverlay() }

    override fun onDestroy() {
        pendingScan?.let { handler.removeCallbacks(it) }
        Settings.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        removeOverlay()
        super.onDestroy()
    }
}
