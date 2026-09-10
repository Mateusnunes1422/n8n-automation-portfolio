package br.farol.corrida

import java.text.Normalizer

/** NONE = sem dado ou meta nao configurada; a barra fica cinza. */
enum class Level { GREEN, YELLOW, RED, NONE }

data class Verdict(
    val level: Level,          // veredito geral — pinta a borda do cartao
    val rsPerKm: Double?,
    val rsPerHour: Double?,
    val rating: Double?,
    val profitPct: Double?,
    val totalKm: Double,
    val totalMin: Int,
    val fuelCost: Double?,
    val netProfit: Double?,
    val kmLevel: Level,
    val hourLevel: Level,
    val ratingLevel: Level,
    val profitLevel: Level,
    val reasons: List<String>,
    val alerts: List<String>
)

object OfferEvaluator {

    // Margem de tolerancia antes de condenar um numero: bate a meta = verde,
    // chega perto = amarelo, fica longe = vermelho.
    private const val NEAR = 0.85

    // Faixas de margem de lucro depois do combustivel, em %.
    private const val PROFIT_GOOD = 65.0
    private const val PROFIT_FAIR = 45.0

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun levelFor(value: Double?, target: Double): Level = when {
        value == null || target <= 0 -> Level.NONE
        value >= target -> Level.GREEN
        value >= target * NEAR -> Level.YELLOW
        else -> Level.RED
    }

    /** Nota tem escala curta (1 a 5), entao a tolerancia e absoluta. */
    private fun ratingLevelFor(rating: Double?, min: Double): Level = when {
        rating == null || min <= 0 -> Level.NONE
        rating >= min -> Level.GREEN
        rating >= min - 0.15 -> Level.YELLOW
        else -> Level.RED
    }

    private fun profitLevelFor(pct: Double?): Level = when {
        pct == null -> Level.NONE
        pct >= PROFIT_GOOD -> Level.GREEN
        pct >= PROFIT_FAIR -> Level.YELLOW
        else -> Level.RED
    }

    fun evaluate(offer: Offer, cfg: Settings): Verdict {
        val fare = offer.fare ?: 0.0

        // O trecho ate o passageiro gasta combustivel e tempo sem pagar,
        // entao entra na conta por padrao.
        val pickupKm = if (cfg.countPickup) (offer.pickupKm ?: 0.0) else 0.0
        val pickupMin = if (cfg.countPickup) (offer.pickupMin ?: 0) else 0
        val totalKm = pickupKm + (offer.tripKm ?: 0.0)
        val totalMin = pickupMin + (offer.tripMin ?: 0)

        val rsPerKm = if (totalKm > 0) fare / totalKm else null
        val rsPerHour = if (totalMin > 0) fare / (totalMin / 60.0) else null

        val fuelCost = if (cfg.kmPerLiter > 0 && totalKm > 0)
            totalKm / cfg.kmPerLiter * cfg.fuelPrice else null
        val netProfit = fuelCost?.let { fare - it }
        val profitPct = if (netProfit != null && fare > 0) netProfit / fare * 100.0 else null

        val kmLevel = levelFor(rsPerKm, cfg.minRsPerKm)
        val hourLevel = levelFor(rsPerHour, cfg.minRsPerHour)
        val ratingLevel = ratingLevelFor(offer.rating, cfg.minRating)
        val profitLevel = profitLevelFor(profitPct)

        val reasons = mutableListOf<String>()
        val alerts = mutableListOf<String>()

        if (rsPerKm != null) reasons +=
            if (kmLevel == Level.GREEN) "R$/km acima da sua meta"
            else "R$/km abaixo da meta (${fmt(cfg.minRsPerKm)})"

        if (rsPerHour != null) reasons +=
            if (hourLevel == Level.GREEN) "R$/hora acima da sua meta"
            else "R$/hora abaixo da meta (${fmt(cfg.minRsPerHour)})"

        if (ratingLevel == Level.RED && offer.rating != null) {
            alerts += "Nota ${fmtRating(offer.rating)}"
        }

        val haystack = norm(offer.lines.joinToString(" "))
        for (w in cfg.riskWords) {
            val nw = norm(w)
            if (nw.isNotBlank() && haystack.contains(nw)) alerts += "Local de risco: $w"
        }

        // Nao cobrir o combustivel e veto: nao importa o resto.
        val losesMoney = netProfit != null && netProfit <= 0
        if (losesMoney) reasons += "Nao paga nem o combustivel"

        val level = when {
            losesMoney -> Level.RED
            alerts.isNotEmpty() && kmLevel != Level.GREEN -> Level.RED
            kmLevel == Level.GREEN && hourLevel == Level.GREEN &&
                alerts.isEmpty() && ratingLevel != Level.RED -> Level.GREEN
            kmLevel == Level.RED && hourLevel == Level.RED -> Level.RED
            else -> Level.YELLOW
        }

        return Verdict(
            level, rsPerKm, rsPerHour, offer.rating, profitPct,
            totalKm, totalMin, fuelCost, netProfit,
            kmLevel, hourLevel, ratingLevel, profitLevel,
            reasons, alerts
        )
    }

    fun fmt(v: Double): String = "R$ %.2f".format(v).replace(".", ",")
    fun fmtRating(v: Double): String = "%.2f".format(v).replace(".", ",")
}
