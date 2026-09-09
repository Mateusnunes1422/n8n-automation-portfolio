package br.farol.corrida

/**
 * Uma oferta de corrida lida da tela do app (Uber / 99).
 * Todos os campos são opcionais porque o layout dos apps muda com frequência —
 * o avaliador trabalha com o que conseguiu extrair.
 */
data class Offer(
    val fare: Double? = null,
    val pickupMin: Int? = null,
    val pickupKm: Double? = null,
    val tripMin: Int? = null,
    val tripKm: Double? = null,
    val rating: Double? = null,
    val lines: List<String> = emptyList()
) {
    /** Tem o mínimo pra valer a pena calcular: valor e alguma distância. */
    val isUsable: Boolean
        get() = fare != null && fare > 0 && ((tripKm ?: 0.0) + (pickupKm ?: 0.0)) > 0

    /** Assinatura pra não reprocessar/repiscar a mesma oferta várias vezes. */
    val signature: String
        get() = "$fare|$pickupMin|$pickupKm|$tripMin|$tripKm"
}

object OfferParser {

    // R$ 18,45   |   R$18,45   |   R$ 1.234,56
    private val MONEY = Regex("""R\$\s*([\d.]*\d)[,.](\d{2})""")

    // "12 min (4,2 km)"  — formato mais comum do Uber
    private val MIN_THEN_KM = Regex(
        """(\d{1,3})\s*min[^\d\n]{0,14}?(\d{1,3}(?:[.,]\d{1,2})?)\s*km""",
        RegexOption.IGNORE_CASE
    )

    // "4,2 km · 12 min"  — ordem invertida
    private val KM_THEN_MIN = Regex(
        """(\d{1,3}(?:[.,]\d{1,2})?)\s*km[^\d\n]{0,14}?(\d{1,3})\s*min""",
        RegexOption.IGNORE_CASE
    )

    private val LONE_MIN = Regex("""(\d{1,3})\s*min""", RegexOption.IGNORE_CASE)
    private val LONE_KM = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*km""", RegexOption.IGNORE_CASE)

    // Nota do passageiro: 4,89 / 5,0 / 4.7 — sempre entre 1 e 5.
    private val RATING = Regex("""\b([1-5][,.]\d{1,2})\b""")

    private fun num(s: String): Double? =
        s.replace(".", "").replace(",", ".").toDoubleOrNull()
            ?: s.replace(",", ".").toDoubleOrNull()

    private fun money(m: MatchResult): Double? {
        val whole = m.groupValues[1].replace(".", "")
        val cents = m.groupValues[2]
        return "$whole.$cents".toDoubleOrNull()
    }

    /**
     * Extrai a oferta de uma lista de textos lidos da tela (um por nó de UI).
     */
    fun parse(lines: List<String>): Offer {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val joined = clean.joinToString("\n")

        // ----- Valor da corrida -----
        // Pega o maior valor da tela: o total costuma ser maior que bônus/taxas.
        val fare = MONEY.findAll(joined).mapNotNull { money(it) }.maxOrNull()

        // ----- Pares (minutos, km) -----
        // Uber mostra dois: primeiro até o passageiro, depois a viagem.
        val pairs = mutableListOf<Pair<Int, Double>>()
        for (line in clean) {
            val direct = MIN_THEN_KM.find(line)
            if (direct != null) {
                val mn = direct.groupValues[1].toIntOrNull()
                val km = num(direct.groupValues[2])
                if (mn != null && km != null) pairs += mn to km
                continue
            }
            val inverted = KM_THEN_MIN.find(line)
            if (inverted != null) {
                val km = num(inverted.groupValues[1])
                val mn = inverted.groupValues[2].toIntOrNull()
                if (mn != null && km != null) pairs += mn to km
            }
        }

        // Fallback: minutos e km soltos em nós separados, casados pela ordem.
        if (pairs.isEmpty()) {
            val mins = LONE_MIN.findAll(joined).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            val kms = LONE_KM.findAll(joined).mapNotNull { num(it.groupValues[1]) }.toList()
            for (i in 0 until minOf(mins.size, kms.size)) pairs += mins[i] to kms[i]
        }

        val pickup = pairs.getOrNull(0)
        val trip = pairs.getOrNull(1)

        // ----- Nota do passageiro -----
        // Um "3,0" solto pode ser nota, mas "3,0 km" e "R$ 3,00" não são.
        // Descarta o que vier logo depois de "R$" e o que for seguido de unidade.
        val unitAfter = Regex("""^\s*(km|min|h\b|%|R\$)""", RegexOption.IGNORE_CASE)
        val ratings = RATING.findAll(joined)
            .filter { m ->
                val before = joined.substring(maxOf(0, m.range.first - 4), m.range.first)
                val from = m.range.last + 1
                val after = joined.substring(from, minOf(joined.length, from + 6))
                !before.contains("R$") && !unitAfter.containsMatchIn(after)
            }
            .mapNotNull { m -> num(m.groupValues[1])?.takeIf { it in 1.0..5.0 } }
            .toList()

        // Nota costuma vir com duas casas (4,82). Se houver uma assim, é ela.
        val rating = ratings.firstOrNull { it * 100 % 10 != 0.0 } ?: ratings.firstOrNull()

        return if (pairs.size >= 2) {
            Offer(fare, pickup?.first, pickup?.second, trip?.first, trip?.second, rating, clean)
        } else {
            // Só um par: é a viagem em si, não sabemos o trecho até o passageiro.
            Offer(fare, null, null, pickup?.first, pickup?.second, rating, clean)
        }
    }
}
