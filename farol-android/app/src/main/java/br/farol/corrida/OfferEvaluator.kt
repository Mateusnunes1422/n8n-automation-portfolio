package br.farol.corrida

import java.text.Normalizer

enum class Level { GREEN, YELLOW, RED }

data class Verdict(
    val level: Level,
    val rsPerKm: Double?,
    val rsPerHour: Double?,
    val totalKm: Double,
    val totalMin: Int,
    val fuelCost: Double?,
    val netProfit: Double?,
    val reasons: List<String>,
    val alerts: List<String>
)

object OfferEvaluator {

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    fun evaluate(offer: Offer, cfg: Settings): Verdict {
        val fare = offer.fare ?: 0.0

        // Distância e tempo considerados. O trecho até o passageiro é gasto real
        // de combustível e de tempo, então entra na conta por padrão.
        val pickupKm = if (cfg.countPickup) (offer.pickupKm ?: 0.0) else 0.0
        val pickupMin = if (cfg.countPickup) (offer.pickupMin ?: 0) else 0
        val totalKm = pickupKm + (offer.tripKm ?: 0.0)
        val totalMin = pickupMin + (offer.tripMin ?: 0)

        val rsPerKm = if (totalKm > 0) fare / totalKm else null
        val rsPerHour = if (totalMin > 0) fare / (totalMin / 60.0) else null

        // Custo de combustível estimado do trajeto todo.
        val fuelCost = if (cfg.kmPerLiter > 0 && totalKm > 0)
            totalKm / cfg.kmPerLiter * cfg.fuelPrice else null
        val netProfit = fuelCost?.let { fare - it }

        val reasons = mutableListOf<String>()
        val alerts = mutableListOf<String>()

        val kmOk = rsPerKm != null && rsPerKm >= cfg.minRsPerKm
        val hourOk = rsPerHour != null && rsPerHour >= cfg.minRsPerHour

        if (rsPerKm != null) {
            reasons += if (kmOk) "R$/km acima da sua meta" else "R$/km abaixo da meta (${fmt(cfg.minRsPerKm)})"
        }
        if (rsPerHour != null) {
            reasons += if (hourOk) "R$/hora acima da sua meta" else "R$/hora abaixo da meta (${fmt(cfg.minRsPerHour)})"
        }

        // Nota do passageiro
        var ratingBad = false
        if (offer.rating != null && cfg.minRating > 0 && offer.rating < cfg.minRating) {
            ratingBad = true
            alerts += "Nota ${fmtRating(offer.rating)} (sua mínima: ${fmtRating(cfg.minRating)})"
        }

        // Palavras de risco em qualquer texto da oferta
        val haystack = norm(offer.lines.joinToString(" "))
        for (w in cfg.riskWords) {
            val nw = norm(w)
            if (nw.isNotBlank() && haystack.contains(nw)) alerts += "Local de risco: $w"
        }

        // Prejuízo depois do combustível é veto absoluto.
        val losesMoney = netProfit != null && netProfit <= 0

        val level = when {
            losesMoney -> Level.RED
            alerts.isNotEmpty() && !kmOk -> Level.RED
            kmOk && hourOk && !ratingBad && alerts.isEmpty() -> Level.GREEN
            kmOk || hourOk -> Level.YELLOW
            else -> Level.RED
        }

        if (losesMoney) reasons += "Não paga nem o combustível"

        return Verdict(level, rsPerKm, rsPerHour, totalKm, totalMin, fuelCost, netProfit, reasons, alerts)
    }

    fun fmt(v: Double): String = "R$ %.2f".format(v).replace(".", ",")
    fun fmtRating(v: Double): String = "%.2f".format(v).replace(".", ",")
}
