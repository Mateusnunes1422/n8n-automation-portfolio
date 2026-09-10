package br.farol.corrida

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Uma corrida que entrou na conta do dia. */
data class Ride(
    val at: Long,
    val fare: Double,
    val km: Double,
    val minutes: Int,
    /** true = deduzida pelo app; false = lancada a mao pelo motorista. */
    val auto: Boolean
)

data class Totals(
    val fare: Double,
    val km: Double,
    val minutes: Int,
    val count: Int
) {
    val perKm: Double? get() = if (km > 0) fare / km else null
    val perHour: Double? get() = if (minutes > 0) fare / (minutes / 60.0) else null
}

/**
 * Registro das corridas do dia.
 *
 * O Farol so enxerga ofertas, nunca o extrato: o que esta aqui e o que ele
 * deduziu ter sido aceito, pelo valor que a oferta anunciava. Serve para
 * acompanhar o dia enquanto ele acontece, nao para conferir pagamento — o
 * numero oficial e sempre o do app de corrida.
 */
object RideLog {

    /**
     * Sinais de que a oferta virou viagem. Se algum aparecer na tela logo
     * depois de a oferta sumir, contamos como aceita.
     */
    private val ACCEPTED_MARKERS = listOf(
        "a caminho", "chegando", "iniciar viagem", "iniciar corrida",
        "cheguei", "em viagem", "finalizar viagem", "cancelar viagem",
        "buscar passageiro", "aguardando o passageiro", "inicie a viagem"
    )

    fun looksAccepted(lines: List<String>): Boolean {
        val hay = lines.joinToString(" ").lowercase()
        return ACCEPTED_MARKERS.any { hay.contains(it) }
    }

    fun totals(rides: List<Ride>) = Totals(
        fare = rides.sumOf { it.fare },
        km = rides.sumOf { it.km },
        minutes = rides.sumOf { it.minutes },
        count = rides.size
    )

    // ---------------- Persistencia ----------------

    private fun keyFor(date: LocalDate) = "log_$date"

    private fun dateOf(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun ridesOn(ctx: Context, date: LocalDate = LocalDate.now()): List<Ride> {
        val raw = Settings.prefs(ctx).getString(keyFor(date), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Ride(
                    at = o.getLong("at"),
                    fare = o.getDouble("fare"),
                    km = o.getDouble("km"),
                    minutes = o.getInt("min"),
                    auto = o.optBoolean("auto", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, date: LocalDate, rides: List<Ride>) {
        val arr = JSONArray()
        rides.forEach { r ->
            arr.put(
                JSONObject()
                    .put("at", r.at)
                    .put("fare", r.fare)
                    .put("km", r.km)
                    .put("min", r.minutes)
                    .put("auto", r.auto)
            )
        }
        Settings.prefs(ctx).edit().putString(keyFor(date), arr.toString()).apply()
    }

    fun add(ctx: Context, ride: Ride) {
        val date = dateOf(ride.at)
        save(ctx, date, ridesOn(ctx, date) + ride)
    }

    fun removeAt(ctx: Context, index: Int, date: LocalDate = LocalDate.now()) {
        val rides = ridesOn(ctx, date).toMutableList()
        if (index in rides.indices) {
            rides.removeAt(index)
            save(ctx, date, rides)
        }
    }

    fun clear(ctx: Context, date: LocalDate = LocalDate.now()) {
        Settings.prefs(ctx).edit().remove(keyFor(date)).apply()
    }

    /** Apaga registros com mais de [dias] dias, para as preferencias nao crescerem sem fim. */
    fun pruneOlderThan(ctx: Context, dias: Long = 30) {
        val limite = LocalDate.now().minusDays(dias)
        val prefs = Settings.prefs(ctx)
        val velhas = prefs.all.keys.filter { k ->
            k.startsWith("log_") && runCatching { LocalDate.parse(k.removePrefix("log_")) < limite }
                .getOrDefault(false)
        }
        if (velhas.isNotEmpty()) {
            prefs.edit().apply { velhas.forEach { remove(it) } }.apply()
        }
    }
}
