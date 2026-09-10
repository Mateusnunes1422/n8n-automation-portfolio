package br.farol.corrida

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import androidx.core.content.ContextCompat

/**
 * Le o texto da oferta na tela do app de corrida e mostra o cartao por cima.
 *
 * A sobreposicao e declarada NOT_TOUCHABLE: ela nunca cobre nem intercepta o
 * botao de aceitar. O app so informa; quem decide e o motorista.
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
        private const val CORNER_DP = 18f
        private const val BORDER_DP = 3f
    }

    /**
     * Recarrega as metas so quando o motorista muda alguma coisa na tela de
     * configuracao. Ler as preferencias a cada evento de tela seria desperdicio:
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

        // Fora do modo aprendizado so olhamos os apps de corrida conhecidos.
        // Com ele ligado olhamos qualquer tela, para descobrir o nome do pacote
        // quando o Uber ou a 99 aparecem com um pacote que ainda nao esta na lista.
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
            // Numa tela de app desconhecido, so guarda se parecer uma oferta.
            // Evita registrar tela de banco, conversa e afins durante o diagnostico.
            if (monitored || joined.contains("R$")) {
                Settings.saveLastCapture(this, pkg, joined)
            }
        }

        // Cartao so aparece nos apps de corrida conhecidos.
        if (!monitored) return

        val offer = OfferParser.parse(texts)
        val now = System.currentTimeMillis()

        if (!offer.isUsable) {
            if (now - lastSeenAt > HIDE_AFTER_MS) {
                hideOverlay()
                lastSignature = null
            }
            return
        }

        lastSeenAt = now
        if (offer.signature == lastSignature) return   // mesma oferta, ja mostrada
        lastSignature = offer.signature

        showOverlay(pkg, OfferEvaluator.evaluate(offer, cfg))
    }

    /** Percorre a arvore de nos juntando todo texto visivel. */
    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > MAX_NODES) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1)
    }

    // ---------------- Sobreposicao ----------------

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun colorOf(level: Level) = ContextCompat.getColor(
        this,
        when (level) {
            Level.GREEN -> R.color.lvl_green
            Level.YELLOW -> R.color.lvl_yellow
            Level.RED -> R.color.lvl_red
            Level.NONE -> R.color.lvl_none
        }
    )

    private fun badgeFor(pkg: String) = when {
        pkg.contains("ubercab") -> "Uber"
        pkg.contains("99") || pkg.contains("taxi") -> "99"
        else -> "App"
    }

    private fun ensureOverlay(): View? {
        overlay?.let { return it }
        if (!AndroidSettings.canDrawOverlays(this)) return null
        val wm = windowManager ?: return null

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_card, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // Nunca rouba o toque: o botao de aceitar continua funcionando.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(40f).toInt()
        }

        return try {
            wm.addView(view, params)
            overlay = view
            view
        } catch (e: Exception) {
            null
        }
    }

    private fun showOverlay(pkg: String, v: Verdict) {
        val view = ensureOverlay() ?: return

        // Fundo escuro com borda na cor do veredito geral.
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(CORNER_DP)
            setColor(ContextCompat.getColor(this@RideAccessibilityService, R.color.card_bg))
            setStroke(dp(BORDER_DP).toInt(), colorOf(v.level))
        }
        view.findViewById<View>(R.id.card).background = bg

        view.findViewById<TextView>(R.id.appBadge).text = badgeFor(pkg)

        val km = "%.1f".format(v.totalKm).replace(".", ",")
        view.findViewById<TextView>(R.id.headline).text = "$km km · ${v.totalMin} min"

        setMetric(view, R.id.kmBar, R.id.kmValue, v.kmLevel,
            v.rsPerKm?.let { "%.2f".format(it).replace(".", ",") })

        setMetric(view, R.id.hourBar, R.id.hourValue, v.hourLevel,
            v.rsPerHour?.let { "%.0f".format(it) })

        setMetric(view, R.id.ratingBar, R.id.ratingValue, v.ratingLevel,
            v.rating?.let { OfferEvaluator.fmtRating(it) })

        setMetric(view, R.id.profitBar, R.id.profitValue, v.profitLevel,
            v.profitPct?.let { "%.0f".format(it) })

        val alertView = view.findViewById<TextView>(R.id.alert)
        if (v.alerts.isNotEmpty()) {
            alertView.visibility = View.VISIBLE
            alertView.text = "⚠ " + v.alerts.joinToString(" · ")
        } else alertView.visibility = View.GONE

        view.visibility = View.VISIBLE
    }

    private fun setMetric(root: View, barId: Int, valueId: Int, level: Level, text: String?) {
        root.findViewById<View>(barId).setBackgroundColor(colorOf(level))
        root.findViewById<TextView>(valueId).text = text ?: "--"
    }

    private fun hideOverlay() { overlay?.visibility = View.GONE }

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
