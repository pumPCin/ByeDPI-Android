package io.github.dovecoteescapee.byedpi.activities

import android.net.VpnService
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.dovecoteescapee.byedpi.data.AppStatus
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import io.github.dovecoteescapee.byedpi.utility.mode

class ToggleActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ToggleServiceActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toggleService()
        finish()
    }

    private fun toggleService() {
        val (status) = appStatus
        when (status) {
            AppStatus.Halted -> {
                val mode = getPreferences().mode()

                if (mode == Mode.VPN && VpnService.prepare(this) != null) {
                    return
                }

                ServiceManager.start(this, mode)
            }
            AppStatus.Running -> {
                ServiceManager.stop(this)
            }
        }
    }
}
