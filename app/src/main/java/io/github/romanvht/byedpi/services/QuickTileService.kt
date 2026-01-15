package io.github.romanvht.byedpi.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    companion object {
        private const val TAG = "QuickTileService"
    }

    private var appTile: Tile? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Tile broadcast received")
            updateStatus()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.i(TAG, "Tile added")
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.i(TAG, "Tile removed")
    }

    override fun onStartListening() {
        super.onStartListening()
        appTile = qsTile
        updateStatus()
    }

    override fun onStopListening() {
        super.onStopListening()
        appTile = null
    }

    override fun onClick() {
        super.onClick()
        handleClick()
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

        Log.i(TAG, "Toggle tile")
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
        appTile?.apply {
            state = newState
            updateTile()
        }
    }
}
