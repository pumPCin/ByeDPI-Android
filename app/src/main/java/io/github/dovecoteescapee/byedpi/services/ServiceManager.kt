package io.github.dovecoteescapee.byedpi.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.data.START_ACTION
import io.github.dovecoteescapee.byedpi.data.STOP_ACTION

object ServiceManager {
    private val TAG: String = ServiceManager::class.java.simpleName

    fun start(context: Context, mode: Mode) {
        when (mode) {
            Mode.VPN -> {
                val intent = Intent(context, ByeDpiVpnService::class.java)
                intent.action = START_ACTION
                ContextCompat.startForegroundService(context, intent)
            }

            Mode.Proxy -> {
                val intent = Intent(context, ByeDpiProxyService::class.java)
                intent.action = START_ACTION
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun stop(context: Context) {
        val (_, mode) = appStatus
        when (mode) {
            Mode.VPN -> {
                val intent = Intent(context, ByeDpiVpnService::class.java)
                intent.action = STOP_ACTION
                ContextCompat.startForegroundService(context, intent)
            }

            Mode.Proxy -> {
                val intent = Intent(context, ByeDpiProxyService::class.java)
                intent.action = STOP_ACTION
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
