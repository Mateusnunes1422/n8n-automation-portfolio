package br.farol.corrida

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
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
        findViewById<Button>(R.id.btnSave).setOnClickListener { save(); showSaved() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { save(); runTest() }
        findViewById<Button>(R.id.btnCapture).setOnClickListener { showCapture() }

        loadIntoFields()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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

    private fun save() {
        val old = Settings.load(this)
        Settings.save(
            this,
            old.copy(
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
        )
    }

    private fun showSaved() {
        AlertDialog.Builder(this)
            .setTitle("Salvo")
            .setMessage("Suas metas foram guardadas. O Farol já passa a usar elas na próxima oferta.")
            .setPositiveButton("Ok", null)
            .show()
    }

    /** Roda o cálculo numa oferta de exemplo, pra conferir se as metas fazem sentido. */
    private fun runTest() {
        val cfg = Settings.load(this)
        val exemplo = Offer(
            fare = 21.50, pickupMin = 6, pickupKm = 2.1,
            tripMin = 19, tripKm = 8.4, rating = 4.82,
            lines = listOf("R$ 21,50", "6 min (2,1 km)", "19 min (8,4 km)", "4,82")
        )
        val v = OfferEvaluator.evaluate(exemplo, cfg)
        val sb = StringBuilder()
        sb.append("Oferta de exemplo: R$ 21,50 · 2,1 km até o passageiro · 8,4 km de viagem · 25 min\n\n")
        sb.append("Veredito: ").append(
            when (v.level) {
                Level.GREEN -> "ACEITAR (verde)"
                Level.YELLOW -> "AVALIAR (amarelo)"
                Level.RED -> "RECUSAR (vermelho)"
            }
        ).append("\n\n")
        v.rsPerKm?.let { sb.append("R$/km: ").append("%.2f".format(it).replace(".", ",")).append("\n") }
        v.rsPerHour?.let { sb.append("R$/hora: ").append("%.2f".format(it).replace(".", ",")).append("\n") }
        v.fuelCost?.let { sb.append("Combustível: ").append(OfferEvaluator.fmt(it)).append("\n") }
        v.netProfit?.let { sb.append("Líquido: ").append(OfferEvaluator.fmt(it)).append("\n") }
        if (v.reasons.isNotEmpty()) sb.append("\n").append(v.reasons.joinToString("\n") { "• $it" })
        if (v.alerts.isNotEmpty()) sb.append("\n\n⚠ ").append(v.alerts.joinToString("\n⚠ "))

        AlertDialog.Builder(this).setTitle("Teste").setMessage(sb.toString())
            .setPositiveButton("Ok", null).show()
    }

    /** Modo aprendizado: mostra o texto cru lido da última tela. */
    private fun showCapture() {
        val (pkg, text) = Settings.lastCapture(this)
        val msg = if (text.isBlank())
            "Nada capturado ainda.\n\nLigue o \"modo aprendizado\", abra o app de corrida e espere uma oferta aparecer. Depois volte aqui.\n\nSe o Farol não estiver lendo direito, me mande esse texto que eu ajusto a leitura."
        else "Pacote: $pkg\n\n$text"
        AlertDialog.Builder(this).setTitle("Última leitura da tela").setMessage(msg)
            .setPositiveButton("Ok", null).show()
    }

    private fun refreshStatus() {
        val accOn = isAccessibilityEnabled(this)
        statusAcc.text = if (accOn) "✅ Leitura de tela ligada" else "❌ Leitura de tela desligada"

        val overlayOn = AndroidSettings.canDrawOverlays(this)
        statusOverlay.text =
            if (overlayOn) "✅ Sobreposição permitida" else "❌ Sobreposição não permitida"
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
