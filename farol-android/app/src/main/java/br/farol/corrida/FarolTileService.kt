package br.farol.corrida

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Atalho de liga/desliga na aba de notificacoes.
 *
 * Existe porque desligar o Farol e algo que se faz com o carro em movimento —
 * abrir o app so pra isso seria pior. Aqui e um toque.
 */
@RequiresApi(Build.VERSION_CODES.N)
class FarolTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        sync()
    }

    override fun onClick() {
        super.onClick()
        val novo = !Settings.isEnabled(this)
        Settings.setEnabled(this, novo)
        sync()
    }

    private fun sync() {
        val tile: Tile = qsTile ?: return
        val ligado = Settings.isEnabled(this)
        tile.state = if (ligado) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (ligado) "Lendo ofertas" else "Desligado"
        }
        tile.updateTile()
    }
}
