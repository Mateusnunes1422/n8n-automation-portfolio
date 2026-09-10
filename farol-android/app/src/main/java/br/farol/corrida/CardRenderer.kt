package br.farol.corrida

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Desenha o cartao da oferta.
 *
 * Serve tanto a sobreposicao que aparece por cima do app de corrida quanto a
 * previa dentro da tela de configuracao: as duas passam por aqui, entao o que
 * o motorista ve na previa e exatamente o que ele vera na rua.
 */
object CardRenderer {

    private const val CORNER_DP = 18f
    private const val BORDER_DP = 3f

    fun colorOf(ctx: Context, level: Level): Int = ContextCompat.getColor(
        ctx,
        when (level) {
            Level.GREEN -> R.color.lvl_green
            Level.YELLOW -> R.color.lvl_yellow
            Level.RED -> R.color.lvl_red
            Level.NONE -> R.color.lvl_none
        }
    )

    private fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    fun render(root: View, appLabel: String, v: Verdict) {
        val ctx = root.context

        // Fundo escuro com a borda na cor do veredito geral.
        root.findViewById<View>(R.id.card).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, CORNER_DP)
            setColor(ContextCompat.getColor(ctx, R.color.card_bg))
            setStroke(dp(ctx, BORDER_DP).toInt(), colorOf(ctx, v.level))
        }

        root.findViewById<TextView>(R.id.appBadge).text = appLabel

        val km = "%.1f".format(v.totalKm).replace(".", ",")
        root.findViewById<TextView>(R.id.headline).text = "$km km · ${v.totalMin} min"

        metric(root, R.id.kmBar, R.id.kmValue, v.kmLevel,
            v.rsPerKm?.let { "%.2f".format(it).replace(".", ",") })

        metric(root, R.id.hourBar, R.id.hourValue, v.hourLevel,
            v.rsPerHour?.let { "%.0f".format(it) })

        metric(root, R.id.ratingBar, R.id.ratingValue, v.ratingLevel,
            v.rating?.let { OfferEvaluator.fmtRating(it) })

        metric(root, R.id.profitBar, R.id.profitValue, v.profitLevel,
            v.profitPct?.let { "%.0f".format(it) })

        val alert = root.findViewById<TextView>(R.id.alert)
        if (v.alerts.isNotEmpty()) {
            alert.visibility = View.VISIBLE
            alert.text = "⚠ " + v.alerts.joinToString(" · ")
        } else alert.visibility = View.GONE
    }

    private fun metric(root: View, barId: Int, valueId: Int, level: Level, text: String?) {
        root.findViewById<View>(barId).setBackgroundColor(colorOf(root.context, level))
        root.findViewById<TextView>(valueId).text = text ?: "--"
    }

    /**
     * Ofertas de exemplo para a previa, calculadas a partir das metas do
     * motorista: assim a previa mostra numeros coerentes com o que ele
     * configurou, em vez de valores fixos que podem nem fazer sentido pra ele.
     */
    fun sampleOffer(cfg: Settings, level: Level): Offer {
        val pickupKm = 2.1; val pickupMin = 6
        val tripKm = 8.4; val tripMin = 19
        val totalKm = pickupKm + tripKm
        val totalH = (pickupMin + tripMin) / 60.0

        // Valor que empata exatamente com a meta mais exigente das duas.
        val naMeta = maxOf(cfg.minRsPerKm * totalKm, cfg.minRsPerHour * totalH)
        val fator = when (level) {
            Level.GREEN -> 1.35   // folgado acima
            Level.YELLOW -> 0.90  // dentro da tolerancia, mas abaixo
            else -> 0.50          // bem longe
        }
        val rating = when (level) {
            Level.GREEN -> 4.92
            Level.YELLOW -> 4.78
            else -> 4.55
        }
        return Offer(
            fare = naMeta * fator,
            pickupMin = pickupMin, pickupKm = pickupKm,
            tripMin = tripMin, tripKm = tripKm,
            rating = rating,
            lines = listOf("Exemplo", "Rua A, 100", "Rua B, 200")
        )
    }
}
