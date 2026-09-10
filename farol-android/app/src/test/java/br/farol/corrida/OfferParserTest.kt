package br.farol.corrida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {

    /** Tela típica do Uber Motorista. */
    private val uberScreen = listOf(
        "UberX",
        "R$ 21,50",
        "4,82",
        "6 min (2,1 km) de distância",
        "19 min (8,4 km) de viagem",
        "Rua das Flores, 200",
        "Av. Central, 1500"
    )

    @Test
    fun `le valor distancias e nota da tela do Uber`() {
        val o = OfferParser.parse(uberScreen)
        assertEquals(21.50, o.fare!!, 0.001)
        assertEquals(6, o.pickupMin)
        assertEquals(2.1, o.pickupKm!!, 0.001)
        assertEquals(19, o.tripMin)
        assertEquals(8.4, o.tripKm!!, 0.001)
        assertEquals(4.82, o.rating!!, 0.001)
        assertTrue(o.isUsable)
    }

    @Test
    fun `pega o maior valor quando ha bonus na tela`() {
        val o = OfferParser.parse(listOf("R$ 2,50 de bônus", "R$ 18,90", "5 min (1,0 km)", "12 min (5,0 km)"))
        assertEquals(18.90, o.fare!!, 0.001)
    }

    @Test
    fun `entende valor com milhar`() {
        val o = OfferParser.parse(listOf("R$ 1.234,56", "5 min (1,0 km)", "60 min (90,0 km)"))
        assertEquals(1234.56, o.fare!!, 0.001)
    }

    @Test
    fun `entende ordem invertida km depois min`() {
        val o = OfferParser.parse(listOf("R$ 30,00", "2,5 km · 7 min", "10,0 km · 22 min"))
        assertEquals(2.5, o.pickupKm!!, 0.001)
        assertEquals(7, o.pickupMin)
        assertEquals(10.0, o.tripKm!!, 0.001)
        assertEquals(22, o.tripMin)
    }

    @Test
    fun `com um par so trata como viagem sem trecho ate o passageiro`() {
        val o = OfferParser.parse(listOf("R$ 15,00", "20 min (7,0 km)"))
        assertNull(o.pickupKm)
        assertEquals(7.0, o.tripKm!!, 0.001)
    }

    @Test
    fun `nao confunde valor em reais com nota`() {
        // "R$ 4,50" não pode virar nota 4,50
        val o = OfferParser.parse(listOf("R$ 4,50", "10 min (3,0 km)", "20 min (8,0 km)"))
        assertNull(o.rating)
    }

    @Test
    fun `nao confunde distancia em km com nota`() {
        // "3,0 km" cai na faixa 1..5 e tem virgula — nao pode virar nota
        val o = OfferParser.parse(listOf("R$ 25,00", "4 min (3,0 km)", "15 min (5,0 km)"))
        assertNull(o.rating)
    }

    @Test
    fun `prefere a nota com duas casas`() {
        val o = OfferParser.parse(listOf("R$ 25,00", "4,91", "4 min (3,0 km)", "15 min (9,0 km)"))
        assertEquals(4.91, o.rating!!, 0.001)
    }

    @Test
    fun `tela sem oferta nao e utilizavel`() {
        val o = OfferParser.parse(listOf("Você está online", "Procurando corridas"))
        assertTrue(!o.isUsable)
    }

    @Test
    fun `assinatura muda entre ofertas diferentes`() {
        val a = OfferParser.parse(uberScreen)
        val b = OfferParser.parse(listOf("R$ 40,00", "6 min (2,1 km)", "19 min (8,4 km)"))
        assertNotNull(a.signature)
        assertTrue(a.signature != b.signature)
    }
}

class OfferEvaluatorTest {

    private val base = Settings(
        minRsPerKm = 1.60, minRsPerHour = 30.0,
        kmPerLiter = 12.0, fuelPrice = 6.00,
        countPickup = true, minRating = 0.0, riskWords = emptyList()
    )

    private fun offer(fare: Double, pkm: Double, pmin: Int, tkm: Double, tmin: Int, rating: Double? = null) =
        Offer(fare, pmin, pkm, tmin, tkm, rating, listOf("Rua A", "Rua B"))

    @Test
    fun `corrida boa fica verde`() {
        // R$ 40 / 10,5 km = 3,81/km ; 40 / (30/60)h = 80/h
        val v = OfferEvaluator.evaluate(offer(40.0, 2.1, 6, 8.4, 24), base)
        assertEquals(Level.GREEN, v.level)
        assertEquals(3.81, v.rsPerKm!!, 0.01)
        assertEquals(80.0, v.rsPerHour!!, 0.1)
    }

    @Test
    fun `corrida ruim fica vermelha`() {
        // R$ 7 / 14 km = 0,50/km ; 7 / (50/60)h = 8,40/h — abaixo das duas metas
        val v = OfferEvaluator.evaluate(offer(7.0, 4.0, 15, 10.0, 35), base)
        assertEquals(Level.RED, v.level)
    }

    @Test
    fun `so uma meta atendida fica amarela`() {
        // R$ 20 / 5 km = 4,00/km (ok) ; 20 / (60/60)h = 20/h (abaixo de 30)
        val v = OfferEvaluator.evaluate(offer(20.0, 1.0, 10, 4.0, 50), base)
        assertEquals(Level.YELLOW, v.level)
    }

    @Test
    fun `prejuizo apos combustivel e sempre vermelho`() {
        // 100 km a 12 km/l a R$6 = R$50 de combustível, corrida paga R$40
        val v = OfferEvaluator.evaluate(offer(40.0, 0.0, 0, 100.0, 90), base)
        assertEquals(Level.RED, v.level)
        assertTrue(v.netProfit!! < 0)
    }

    @Test
    fun `calcula combustivel e liquido`() {
        // 12 km a 12 km/l = 1 litro = R$ 6,00
        val v = OfferEvaluator.evaluate(offer(30.0, 2.0, 5, 10.0, 25), base)
        assertEquals(6.0, v.fuelCost!!, 0.01)
        assertEquals(24.0, v.netProfit!!, 0.01)
    }

    @Test
    fun `ignorar trecho ate o passageiro muda a conta`() {
        val comPickup = OfferEvaluator.evaluate(offer(20.0, 5.0, 15, 5.0, 15), base)
        val semPickup = OfferEvaluator.evaluate(offer(20.0, 5.0, 15, 5.0, 15), base.copy(countPickup = false))
        assertEquals(2.0, comPickup.rsPerKm!!, 0.01)   // 20 / 10 km
        assertEquals(4.0, semPickup.rsPerKm!!, 0.01)   // 20 / 5 km
    }

    @Test
    fun `palavra de risco acende alerta`() {
        val cfg = base.copy(riskWords = listOf("Morro Azul"))
        val o = Offer(40.0, 6, 2.1, 24, 8.4, null, listOf("Rua A", "Morro Azul, 40"))
        val v = OfferEvaluator.evaluate(o, cfg)
        assertTrue(v.alerts.any { it.contains("Morro Azul") })
    }

    @Test
    fun `palavra de risco ignora acento e caixa`() {
        val cfg = base.copy(riskWords = listOf("cracolandia"))
        val o = Offer(40.0, 6, 2.1, 24, 8.4, null, listOf("Rua A", "CRACOLÂNDIA"))
        val v = OfferEvaluator.evaluate(o, cfg)
        assertTrue(v.alerts.isNotEmpty())
    }

    @Test
    fun `nota abaixo da minima gera alerta e tira o verde`() {
        val cfg = base.copy(minRating = 4.7)
        val v = OfferEvaluator.evaluate(offer(40.0, 2.1, 6, 8.4, 24, rating = 4.5), cfg)
        assertTrue(v.alerts.isNotEmpty())
        assertTrue(v.level != Level.GREEN)
    }

    @Test
    fun `cada metrica ganha sua propria cor`() {
        // R$/km 2,00 (meta 1,60 -> verde) mas R$/hora 24 (meta 30 -> vermelho)
        val v = OfferEvaluator.evaluate(offer(20.0, 2.0, 10, 8.0, 40), base)
        assertEquals(Level.GREEN, v.kmLevel)
        assertEquals(Level.RED, v.hourLevel)
    }

    @Test
    fun `chegar perto da meta fica amarelo`() {
        // 1,45/km contra meta de 1,60 = 90% dela, dentro da tolerancia
        val v = OfferEvaluator.evaluate(offer(14.5, 0.0, 0, 10.0, 20), base)
        assertEquals(Level.YELLOW, v.kmLevel)
    }

    @Test
    fun `meta nao configurada deixa a barra cinza`() {
        // minRating = 0 significa "nao me importo com nota"
        val v = OfferEvaluator.evaluate(offer(40.0, 2.1, 6, 8.4, 24, rating = 3.0), base)
        assertEquals(Level.NONE, v.ratingLevel)
    }

    @Test
    fun `sem nota na oferta a barra fica cinza`() {
        val v = OfferEvaluator.evaluate(offer(40.0, 2.1, 6, 8.4, 24), base.copy(minRating = 4.7))
        assertEquals(Level.NONE, v.ratingLevel)
    }

    @Test
    fun `calcula a margem de lucro em porcentagem`() {
        // 12 km a 12 km/l a R$ 6 = R$ 6 de combustivel numa corrida de R$ 30
        // Sobram R$ 24, ou seja 80% do valor
        val v = OfferEvaluator.evaluate(offer(30.0, 2.0, 5, 10.0, 25), base)
        assertEquals(80.0, v.profitPct!!, 0.1)
        assertEquals(Level.GREEN, v.profitLevel)
    }

    @Test
    fun `margem apertada deixa o lucro vermelho`() {
        // 40 km consomem R$ 20 de combustivel numa corrida de R$ 32: sobra 37%
        val v = OfferEvaluator.evaluate(offer(32.0, 0.0, 0, 40.0, 60), base)
        assertEquals(Level.RED, v.profitLevel)
    }
}
