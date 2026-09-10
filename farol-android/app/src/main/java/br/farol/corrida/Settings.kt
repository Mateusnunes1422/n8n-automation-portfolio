package br.farol.corrida

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuração do motorista. Fica só no aparelho (SharedPreferences),
 * nada é enviado pra lugar nenhum.
 */
data class Settings(
    val minRsPerKm: Double = 1.60,
    val minRsPerHour: Double = 30.0,
    val kmPerLiter: Double = 12.0,
    val fuelPrice: Double = 6.00,
    val countPickup: Boolean = true,
    val minRating: Double = 0.0,
    val riskWords: List<String> = emptyList(),
    val monitoredPackages: Set<String> = DEFAULT_PACKAGES,
    val enabled: Boolean = true,
    val learnMode: Boolean = false
) {
    companion object {
        val DEFAULT_PACKAGES = setOf(
            "com.ubercab.driver",   // Uber Motorista
            "com.taxi.driver",      // 99 Motorista
            "com.taxis99.driver"    // 99 Motorista (variante)
        )

        private const val PREFS = "farol_prefs"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(ctx: Context): Settings {
            val p = prefs(ctx)
            val d = Settings()
            return Settings(
                minRsPerKm = p.getFloat("minRsPerKm", d.minRsPerKm.toFloat()).toDouble(),
                minRsPerHour = p.getFloat("minRsPerHour", d.minRsPerHour.toFloat()).toDouble(),
                kmPerLiter = p.getFloat("kmPerLiter", d.kmPerLiter.toFloat()).toDouble(),
                fuelPrice = p.getFloat("fuelPrice", d.fuelPrice.toFloat()).toDouble(),
                countPickup = p.getBoolean("countPickup", d.countPickup),
                minRating = p.getFloat("minRating", d.minRating.toFloat()).toDouble(),
                riskWords = p.getString("riskWords", "")!!
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                monitoredPackages = p.getStringSet("packages", DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES,
                enabled = p.getBoolean("enabled", d.enabled),
                learnMode = p.getBoolean("learnMode", d.learnMode)
            )
        }

        fun save(ctx: Context, s: Settings) {
            prefs(ctx).edit().apply {
                putFloat("minRsPerKm", s.minRsPerKm.toFloat())
                putFloat("minRsPerHour", s.minRsPerHour.toFloat())
                putFloat("kmPerLiter", s.kmPerLiter.toFloat())
                putFloat("fuelPrice", s.fuelPrice.toFloat())
                putBoolean("countPickup", s.countPickup)
                putFloat("minRating", s.minRating.toFloat())
                putString("riskWords", s.riskWords.joinToString(", "))
                putStringSet("packages", s.monitoredPackages)
                putBoolean("enabled", s.enabled)
                putBoolean("learnMode", s.learnMode)
            }.apply()
        }

        /**
         * Liga e desliga o Farol sem tocar nas outras configuracoes.
         * O servico observa as preferencias, entao o efeito e imediato.
         */
        fun setEnabled(ctx: Context, value: Boolean) {
            prefs(ctx).edit().putBoolean("enabled", value).apply()
        }

        fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", true)

        /** Último texto lido da tela — usado pelo modo aprendizado. */
        fun saveLastCapture(ctx: Context, pkg: String, text: String) {
            prefs(ctx).edit()
                .putString("lastCapturePkg", pkg)
                .putString("lastCapture", text)
                .apply()
        }

        fun lastCapture(ctx: Context): Pair<String, String> {
            val p = prefs(ctx)
            return (p.getString("lastCapturePkg", "") ?: "") to (p.getString("lastCapture", "") ?: "")
        }
    }
}
