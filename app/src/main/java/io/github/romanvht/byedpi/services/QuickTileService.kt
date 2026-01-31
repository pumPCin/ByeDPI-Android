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
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Tile broadcast received: ${intent?.action}")
            updateStatus()
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(STARTED_BROADCAST)
        addAction(STOPPED_BROADCAST)
        addAction(FAILED_BROADCAST)
    }

    override fun onStartListening() {
        super.onStartListening()

        appTile = qsTile
        createReceiver()
        updateStatus()
    }

    override fun onStopListening() {
        super.onStopListening()

        deleteReceiver()
        appTile = null
    }

    override fun onClick() {
        super.onClick()
        handleClick()
    }

    private fun handleClick() {
        val (status) = appStatus
        val mode = getPreferences().mode()

        when (status) {
            AppStatus.Halted -> startService(mode)
            AppStatus.Running -> stopService()
        }

        Log.i(TAG, "Tile clicked")
    }

    private fun startService(mode: Mode) {
        if (mode == Mode.VPN && VpnService.prepare(this) != null) {
            return
        }

        ServiceManager.start(this, mode)
        setState(Tile.STATE_ACTIVE)
    }

    private fun stopService() {
        ServiceManager.stop(this)
        setState(Tile.STATE_INACTIVE)
    }

    private fun updateStatus() {
        val (status) = appStatus

        val newState = when (status) {
            AppStatus.Running -> Tile.STATE_ACTIVE
            AppStatus.Halted -> Tile.STATE_INACTIVE
        }

        setState(newState)
    }

    private fun setState(state: Int) {
        appTile?.apply {
            this.state = state
            updateTile()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun createReceiver() {
        if (receiverRegistered) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        receiverRegistered = true
        Log.d(TAG, "Receiver registered")
    }

    private fun deleteReceiver() {
        if (!receiverRegistered) return

        try {
            unregisterReceiver(receiver)
            Log.d(TAG, "Receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered")
        }

        receiverRegistered = false
    }
}
