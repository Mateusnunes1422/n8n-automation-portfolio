package br.farol.corrida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideLogTest {

    private fun ride(fare: Double, km: Double, min: Int) =
        Ride(at = 0L, fare = fare, km = km, minutes = min, auto = true)

    // ---------- deducao de aceite ----------

    @Test
    fun `tela de viagem em andamento conta como aceita`() {
        assertTrue(RideLog.looksAccepted(listOf("A caminho do passageiro", "Rua X, 100")))
        assertTrue(RideLog.looksAccepted(listOf("Iniciar viagem")))
        assertTrue(RideLog.looksAccepted(listOf("Finalizar viagem", "12,4 km")))
    }

    @Test
    fun `deducao ignora a caixa das letras`() {
        assertTrue(RideLog.looksAccepted(listOf("INICIAR VIAGEM")))
        assertTrue(RideLog.looksAccepted(listOf("a CaMiNhO")))
    }

    @Test
    fun `tela ociosa nao conta como aceita`() {
        assertFalse(RideLog.looksAccepted(listOf("Você está online", "Procurando corridas")))
        assertFalse(RideLog.looksAccepted(listOf("Ganhos de hoje", "R$ 180,00")))
        assertFalse(RideLog.looksAccepted(emptyList()))
    }

    // ---------- totais do dia ----------

    @Test
    fun `soma valor distancia e tempo das corridas`() {
        val t = RideLog.totals(listOf(ride(20.0, 8.0, 25), ride(35.5, 14.0, 40)))
        assertEquals(55.5, t.fare, 0.001)
        assertEquals(22.0, t.km, 0.001)
        assertEquals(65, t.minutes)
        assertEquals(2, t.count)
    }

    @Test
    fun `calcula as medias do dia`() {
        // R$ 60 em 30 km = 2,00/km ; 60 em 1h = 60/h
        val t = RideLog.totals(listOf(ride(30.0, 15.0, 30), ride(30.0, 15.0, 30)))
        assertEquals(2.0, t.perKm!!, 0.001)
        assertEquals(60.0, t.perHour!!, 0.001)
    }

    @Test
    fun `dia vazio nao tem media`() {
        val t = RideLog.totals(emptyList())
        assertEquals(0.0, t.fare, 0.001)
        assertEquals(0, t.count)
        assertNull(t.perKm)
        assertNull(t.perHour)
    }

    @Test
    fun `corrida sem distancia nao quebra a media por km`() {
        val t = RideLog.totals(listOf(ride(12.0, 0.0, 10)))
        assertNull(t.perKm)
        assertEquals(72.0, t.perHour!!, 0.001)
    }
}
