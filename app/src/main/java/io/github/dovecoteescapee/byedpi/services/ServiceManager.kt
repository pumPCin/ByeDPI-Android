package io.github.dovecoteescapee.byedpi.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.dovecoteescapee.byedpi.data.AppStatus
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.data.START_ACTION
import io.github.dovecoteescapee.byedpi.data.STOP_ACTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ServiceManager {
    private val TAG: String = ServiceManager::class.java.simpleName
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    fun restart(context: Context, mode: Mode) {
        if (appStatus.first == AppStatus.Running) {
            stop(context)
            scope.launch {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000L) {
                    if (appStatus.first == AppStatus.Halted) break
                    delay(100)
                }
                start(context, mode)
            }
        }
    }
}
