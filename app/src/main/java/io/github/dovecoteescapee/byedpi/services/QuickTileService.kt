package io.github.dovecoteescapee.byedpi.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import io.github.dovecoteescapee.byedpi.utility.mode

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    companion object {
        private val TAG: String = QuickTileService::class.java.simpleName
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val senderOrd = intent.getIntExtra(SENDER, -1)
            val sender = Sender.entries.getOrNull(senderOrd)
            if (sender == null) {
                Log.w(TAG, "Received intent with unknown sender: $senderOrd")
                return
            }

            when (val action = intent.action) {
                STARTED_BROADCAST,
                STOPPED_BROADCAST -> updateStatus()

                FAILED_BROADCAST -> {
                    Toast.makeText(
                        context,
                        getString(R.string.failed_to_start, sender.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    updateStatus()
                }

                else -> Log.w(TAG, "Unknown action: $action")
            }
        }
    }

    override fun onStartListening() {
        updateStatus()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(STARTED_BROADCAST)
                addAction(STOPPED_BROADCAST)
                addAction(FAILED_BROADCAST)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStopListening() {
        unregisterReceiver(receiver)
    }

    override fun onClick() {
        if (qsTile.state == Tile.STATE_UNAVAILABLE) {
            return
        }

        unlockAndRun(this::handleClick)
    }

    private fun setState(newState: Int) {
        qsTile.apply {
            state = newState
            updateTile()
        }
    }

    private fun updateStatus() {
        val (status) = appStatus
        setState(if (status == AppStatus.Halted) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE)
    }

    private fun handleClick() {
        setState(Tile.STATE_ACTIVE)
        setState(Tile.STATE_UNAVAILABLE)

        val (status) = appStatus
        when (status) {
            AppStatus.Halted -> {
                val mode = getPreferences().mode()

                if (mode == Mode.VPN && VpnService.prepare(this) != null) {
                    return
                }

                ServiceManager.start(this, mode)
            }

            AppStatus.Running -> ServiceManager.stop(this)
        }
    }
}
