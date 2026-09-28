package io.github.romanvht.byedpi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import android.util.Log
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.START_ACTION
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_REBOOT ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // for A15, todo: use wasForceStopped
            if (SystemClock.elapsedRealtime() > 5 * 60 * 1000) {
                return
            }

            val preferences = context.getPreferences()
            val autorunEnabled = preferences.getBoolean("autostart", false)

            if(autorunEnabled) {
                val mode = preferences.mode()
                if (mode == Mode.VPN && VpnService.prepare(context) != null) return
                val pending = goAsync()

                CoroutineScope(Dispatchers.Main.immediate).launch {
                    try {
                        ServiceManager.handleAction(context.applicationContext, START_ACTION, mode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service after boot", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
