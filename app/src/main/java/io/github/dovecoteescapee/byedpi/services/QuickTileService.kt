package io.github.dovecoteescapee.byedpi.services

import android.content.ComponentName
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import io.github.dovecoteescapee.byedpi.utility.mode

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    companion object {
        private const val TAG = "QuickTileService"

        fun updateTile(context: Context) {
            requestListeningState(context, ComponentName(context, QuickTileService::class.java))
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateStatus()
    }

    override fun onClick() {
        if (qsTile.state == Tile.STATE_UNAVAILABLE) return

        unlockAndRun { handleClick() }
    }

    private fun handleClick() {
        val (status) = appStatus
        when (status) {
            AppStatus.Halted -> {
                val mode = getPreferences().mode()

                if (mode == Mode.VPN && VpnService.prepare(this) != null) {
                    return
                }

                ServiceManager.start(this, mode)
                setState(Tile.STATE_ACTIVE)
            }
            AppStatus.Running -> {
                ServiceManager.stop(this)
                setState(Tile.STATE_INACTIVE)
            }
        }

        updateTile(this)
    }

    private fun updateStatus() {
        val (status) = appStatus

        if (status == AppStatus.Running) {
            setState(Tile.STATE_ACTIVE)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
    }

    private fun setState(newState: Int) {
        qsTile?.apply {
            state = newState
            updateTile()
        }
    }
}
