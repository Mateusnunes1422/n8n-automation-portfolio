package br.farol.corrida

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var minKm: EditText
    private lateinit var minHour: EditText
    private lateinit var kmPerLiter: EditText
    private lateinit var fuelPrice: EditText
    private lateinit var minRating: EditText
    private lateinit var riskWords: EditText
    private lateinit var countPickup: MaterialSwitch
    private lateinit var learnMode: MaterialSwitch
    private lateinit var statusAcc: TextView
    private lateinit var statusOverlay: TextView
    private lateinit var previewCard: View
    private lateinit var headerPill: TextView
    private lateinit var masterSwitch: MaterialSwitch
    private lateinit var masterCard: View
    private lateinit var masterTitle: TextView
    private lateinit var masterSub: TextView
    private var previewLevel = Level.GREEN

    override fun onCreate(savedInstanceState: Bundle?) {
        // O cartao que o Farol desenha e escuro; a tela de ajuste segue o mesmo
        // mundo, independente do tema do aparelho.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        minKm = findViewById(R.id.minKm)
        minHour = findViewById(R.id.minHour)
        kmPerLiter = findViewById(R.id.kmPerLiter)
        fuelPrice = findViewById(R.id.fuelPrice)
        minRating = findViewById(R.id.minRating)
        riskWords = findViewById(R.id.riskWords)
        countPickup = findViewById(R.id.countPickup)
        learnMode = findViewById(R.id.learnMode)
        statusAcc = findViewById(R.id.statusAcc)
        statusOverlay = findViewById(R.id.statusOverlay)
        headerPill = findViewById(R.id.headerPill)
        masterSwitch = findViewById(R.id.masterSwitch)
        masterCard = findViewById(R.id.masterCard)
        masterTitle = findViewById(R.id.masterTitle)
        masterSub = findViewById(R.id.masterSub)

        // Estado antes do listener, para o primeiro desenho nao gravar de volta.
        masterSwitch.isChecked = Settings.isEnabled(this)

        // Liga/desliga grava na hora: nao faz sentido exigir "Salvar" para isso.
        masterSwitch.setOnCheckedChangeListener { _, on ->
            Settings.setEnabled(this, on)
            refreshMaster()
            refreshStatus()
        }

        findViewById<Button>(R.id.btnAcc).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        previewCard = findViewById(R.id.previewCard)
        mapOf(
            R.id.tabGreen to Level.GREEN,
            R.id.tabYellow to Level.YELLOW,
            R.id.tabRed to Level.RED
        ).forEach { (id, level) ->
            findViewById<TextView>(id).setOnClickListener {
                previewLevel = level
                refreshPreview()
            }
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save(); showSaved(); refreshPreview() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { save(); runTest() }
        findViewById<Button>(R.id.btnCapture).setOnClickListener { showCapture() }

        loadIntoFields()
        refreshPreview()
        refreshMaster()
    }

    override fun onResume() {
        super.onResume()
        // Pode ter sido desligado pelo atalho da aba de notificacoes.
        val ligado = Settings.isEnabled(this)
        if (masterSwitch.isChecked != ligado) masterSwitch.isChecked = ligado
        refreshMaster()
        refreshStatus()
    }

    /** Estado do interruptor principal: titulo, explicacao e borda do cartao. */
    private fun refreshMaster() {
        val on = Settings.isEnabled(this)
        masterTitle.text = if (on) "Farol ligado" else "Farol desligado"
        masterSub.text = if (on)
            "Quando chegar uma oferta no Uber ou na 99, o cartão aparece por cima."
        else
            "Nenhum cartão vai aparecer. As suas metas continuam guardadas."
        masterCard.setBackgroundResource(
            if (on) R.drawable.bg_surface_on else R.drawable.bg_surface
        )
    }

    private fun loadIntoFields() {
        val s = Settings.load(this)
        minKm.setText(dec(s.minRsPerKm))
        minHour.setText(dec(s.minRsPerHour))
        kmPerLiter.setText(dec(s.kmPerLiter))
        fuelPrice.setText(dec(s.fuelPrice))
        minRating.setText(if (s.minRating > 0) dec(s.minRating) else "")
        riskWords.setText(s.riskWords.joinToString(", "))
        countPickup.isChecked = s.countPickup
        learnMode.isChecked = s.learnMode
    }

    /** Le as metas direto dos campos, para a previa refletir o que esta na tela. */
    private fun fieldSettings(): Settings {
        val old = Settings.load(this)
        return old.copy(
            minRsPerKm = num(minKm) ?: old.minRsPerKm,
            minRsPerHour = num(minHour) ?: old.minRsPerHour,
            kmPerLiter = num(kmPerLiter) ?: old.kmPerLiter,
            fuelPrice = num(fuelPrice) ?: old.fuelPrice,
            minRating = num(minRating) ?: 0.0,
            riskWords = riskWords.text.toString()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() },
            countPickup = countPickup.isChecked,
            learnMode = learnMode.isChecked
        )
    }

    private fun save() = Settings.save(this, fieldSettings())

    /**
     * Mostra o cartao real com uma oferta de exemplo no estado escolhido.
     * Usa o mesmo CardRenderer da sobreposicao, entao a previa nao pode
     * divergir do que aparece na rua.
     */
    private fun refreshPreview() {
        val cfg = fieldSettings()
        val offer = CardRenderer.sampleOffer(cfg, previewLevel)
        CardRenderer.render(previewCard, "Uber", OfferEvaluator.evaluate(offer, cfg))

        listOf(
            R.id.tabGreen to Level.GREEN,
            R.id.tabYellow to Level.YELLOW,
            R.id.tabRed to Level.RED
        ).forEach { (id, level) ->
            val chip = findViewById<TextView>(id)
            val on = level == previewLevel
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            chip.setTextColor(ContextCompat.getColor(this, if (on) R.color.accent else R.color.ink_dim))
        }
    }

    private fun showSaved() {
        AlertDialog.Builder(this)
            .setTitle("Salvo")
            .setMessage("Suas metas foram guardadas. O Farol já passa a usar elas na próxima oferta.")
            .setPositiveButton("Ok", null)
            .show()
    }

    /** Abre a conta por tras do cartao que esta na previa. */
    private fun runTest() {
        val cfg = fieldSettings()
        val offer = CardRenderer.sampleOffer(cfg, previewLevel)
        val v = OfferEvaluator.evaluate(offer, cfg)

        val sb = StringBuilder()
        sb.append("Oferta de exemplo: ").append(OfferEvaluator.fmt(offer.fare ?: 0.0))
            .append(" · ").append("%.1f".format(v.totalKm).replace(".", ","))
            .append(" km · ").append(v.totalMin).append(" min\n\n")

        sb.append("Veredito: ").append(
            when (v.level) {
                Level.GREEN -> "ACEITAR (borda verde)"
                Level.YELLOW -> "AVALIAR (borda amarela)"
                Level.RED -> "RECUSAR (borda vermelha)"
                Level.NONE -> "SEM DADOS (borda cinza)"
            }
        ).append("\n\n")

        v.rsPerKm?.let {
            sb.append("R$/km: ").append("%.2f".format(it).replace(".", ","))
                .append("  (sua meta: ").append(dec(cfg.minRsPerKm)).append(")\n")
        }
        v.rsPerHour?.let {
            sb.append("R$/hora: ").append("%.2f".format(it).replace(".", ","))
                .append("  (sua meta: ").append(dec(cfg.minRsPerHour)).append(")\n")
        }
        v.fuelCost?.let { sb.append("Combustível: ").append(OfferEvaluator.fmt(it)).append("\n") }
        v.netProfit?.let { sb.append("Líquido: ").append(OfferEvaluator.fmt(it)) }
        v.profitPct?.let { sb.append("  (").append("%.0f".format(it)).append("%)\n") }

        if (v.reasons.isNotEmpty()) sb.append("\n").append(v.reasons.joinToString("\n") { "• $it" })
        if (v.alerts.isNotEmpty()) sb.append("\n\n⚠ ").append(v.alerts.joinToString("\n⚠ "))

        AlertDialog.Builder(this).setTitle("A conta do cartão").setMessage(sb.toString())
            .setPositiveButton("Ok", null).show()
    }

    /** Modo aprendizado: mostra o texto cru lido da última tela. */
    private fun showCapture() {
        val (pkg, text) = Settings.lastCapture(this)
        val cfg = Settings.load(this)
        val msg = if (text.isBlank())
            "Nada capturado ainda.\n\n" +
                "Ligue o \"modo aprendizado\", abra o app de corrida e espere uma oferta " +
                "aparecer na tela. Depois volte aqui.\n\n" +
                "Apps que o Farol acompanha hoje:\n" + cfg.monitoredPackages.joinToString("\n") { "• $it" }
        else {
            val conhecido = if (pkg in cfg.monitoredPackages) "✅ na lista"
            else "⚠️ FORA da lista — é por isso que o card não aparece"
            "Pacote: $pkg ($conhecido)\n\n$text"
        }
        AlertDialog.Builder(this).setTitle("Última leitura da tela").setMessage(msg)
            .setPositiveButton("Ok", null).show()
    }

    private fun refreshStatus() {
        val accOn = isAccessibilityEnabled(this)
        val overlayOn = AndroidSettings.canDrawOverlays(this)

        statusLine(statusAcc, accOn, "Leitura de tela ligada", "Leitura de tela desligada")
        statusLine(statusOverlay, overlayOn, "Sobreposição permitida", "Sobreposição não permitida")

        val ligado = Settings.isEnabled(this)
        val pronto = accOn && overlayOn
        val (texto, cor) = when {
            !ligado -> "● desligado" to R.color.ink_dim
            pronto -> "● no ar" to R.color.accent
            else -> "● falta liberar" to R.color.lvl_yellow
        }
        headerPill.text = texto
        headerPill.setTextColor(ContextCompat.getColor(this, cor))
    }

    private fun statusLine(view: TextView, ok: Boolean, yes: String, no: String) {
        view.text = if (ok) "● $yes" else "● $no"
        view.setTextColor(ContextCompat.getColor(this, if (ok) R.color.accent else R.color.lvl_red))
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = ComponentName(ctx, RideAccessibilityService::class.java).flattenToString()
        val enabled = AndroidSettings.Secure.getString(
            ctx.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (item in splitter) {
            if (item.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun num(e: EditText): Double? =
        e.text.toString().trim().replace(",", ".").toDoubleOrNull()

    private fun dec(v: Double): String = "%.2f".format(v).replace(".", ",")
}
