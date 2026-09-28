package io.github.romanvht.byedpi.services

import android.app.ActivityManager
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.romanvht.byedpi.core.ByeDpiProxyCmdPreferences
import io.github.romanvht.byedpi.core.ByeDpiProxyPreferences
import io.github.romanvht.byedpi.core.ByeDpiProxyUIPreferences
import io.github.romanvht.byedpi.data.Configuration
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PrivateDnsState
import io.github.romanvht.byedpi.data.START_ACTION
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

internal class NativeEngine(context: Context, val mode: Mode = Mode.Proxy, initialPid: Int = 0) {
    companion object {
        private const val TAG = "NativeEngine"
        private const val SESSION_PREFERENCES = "native_session"

        fun configuration(context: Context): Configuration {
            val preferences = context.getPreferences()
            val proxy = ByeDpiProxyPreferences.fromSharedPreferences(preferences, context)
            val args = when (proxy) {
                is ByeDpiProxyCmdPreferences -> proxy.args
                is ByeDpiProxyUIPreferences -> proxy.uiargs
            }
            val (host, port) = preferences.getProxyIpAndPort()
            return Configuration(
                args.toList(), host, port.toInt(),
                dns = if (PrivateDnsUtils.getState(context) is PrivateDnsState.Configured) ""
                    else preferences.getStringNotNull("dns_ip", "1.1.1.1"),
                ipv6 = preferences.getBoolean("ipv6_enable", false),
                appListType = preferences.getStringNotNull("applist_type", "disable"),
                apps = preferences.getSelectedApps(),
            )
        }

        fun serviceClass(mode: Mode): Class<out Service> = when (mode) {
            Mode.VPN -> ByeDpiVpnService::class.java
            Mode.Proxy -> ByeDpiProxyService::class.java
        }

        @Suppress("DEPRECATION")
        fun running(context: Context): Pair<Mode, Int>? {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE).sortedBy { it.activeSince }) {
                if (service.uid != Process.myUid() || service.pid <= 0) continue
                val mode = Mode.entries.firstOrNull { service.service.className == serviceClass(it).name }
                    ?: continue
                return mode to service.pid
            }
            return null
        }
    }

    private val application = context.applicationContext as Application
    private val savedSession = application.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
    private val hello = CompletableDeferred<Unit>()
    private val configured = CompletableDeferred<Unit>()
    private val started = CompletableDeferred<Unit>()
    private val exited = CompletableDeferred<Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var binder: IBinder? = null
    private var remote: Messenger? = null
    private var bound = false
    private var connecting = false
    private var closing = false
    private var forceStopping = false
    private var startSent = false
    private var serviceStarted = initialPid > 0
    private var pid = initialPid
    private var state = EngineProtocol.STATE_IDLE
    private var failure: String? = null
    private var proxyAddress: InetAddress? = null
    private var proxyPort = 0
    private var portReleased = true
    internal val processId: Int get() = pid
    val needsConfiguration: Boolean get() = state == EngineProtocol.STATE_IDLE && !startSent
    var onStopping: (() -> Unit)? = null

    init {
        if (pid > 0 && savedSession.getInt(EngineProtocol.PID, 0) == pid) {
            val host = savedSession.getString(EngineProtocol.HOST, null)
            val port = savedSession.getInt(EngineProtocol.PORT, 0)
            if (host != null && port in 1..65535) {
                setEndpoint(host, port)
                portReleased = false
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        handler.post { onExit() }
    }

    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (exited.isCompleted && message.what != EngineProtocol.FAILED) return@Handler true
        when (message.what) {
            EngineProtocol.HELLO_ACK -> {
                val remotePid = message.data.getInt(EngineProtocol.PID)
                if (remotePid <= 0 || remotePid == Process.myPid() || pid > 0 && pid != remotePid) {
                    hello.completeExceptionally(IllegalStateException("Invalid native process"))
                    return@Handler true
                }
                pid = remotePid
                state = message.data.getInt(EngineProtocol.STATE)
                val host = message.data.getString(EngineProtocol.HOST)
                val port = message.data.getInt(EngineProtocol.PORT)
                if (host != null && port in 1..65535) {
                    setEndpoint(host, port)
                    portReleased = false
                    saveEndpoint()
                }
                hello.complete(Unit)
                if (state != EngineProtocol.STATE_IDLE) configured.complete(Unit)
                when (state) {
                    EngineProtocol.STATE_RUNNING -> started.complete(Unit)
                    EngineProtocol.STATE_STOPPING -> if (!closing) onStopping?.invoke()
                }
                if (closing) sendStop()
            }
            EngineProtocol.STARTED -> {
                state = EngineProtocol.STATE_RUNNING
                started.complete(Unit)
            }
            EngineProtocol.CONFIGURED -> {
                state = EngineProtocol.STATE_STARTING
                configured.complete(Unit)
            }
            EngineProtocol.STOPPING -> {
                state = EngineProtocol.STATE_STOPPING
                if (!closing) onStopping?.invoke()
            }
            EngineProtocol.FAILED -> {
                failure = message.data.getString(EngineProtocol.ERROR) ?: "Native engine stopped"
                portReleased = message.data.getBoolean(EngineProtocol.PORT_RELEASED)
                started.completeExceptionally(IllegalStateException(failure))
            }
        }
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (exited.isCompleted) return
            binder = service
            remote = Messenger(service)
            try {
                service.linkToDeath(deathRecipient, 0)
                send(EngineProtocol.HELLO)
                if (closing) sendStop()
                if (forceStopping) abort()
            } catch (_: RemoteException) {
                onExit()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = onExit()
        override fun onNullBinding(name: ComponentName) = onExit()
        override fun onBindingDied(name: ComponentName) = onExit()
    }

    suspend fun connect(launchService: Boolean = false) {
        check(!closing)
        if (!connecting) {
            connecting = true
            if (launchService) {
                ContextCompat.startForegroundService(application,
                    Intent(application, serviceClass(mode)).setAction(START_ACTION))
                serviceStarted = true
            }
            bound = application.bindService(
                Intent(application, serviceClass(mode)).setAction(EngineProtocol.CONTROL_ACTION),
                connection,
                0,
            )
            check(bound) { "Could not bind native engine" }
        }
        withTimeout(10_000) { hello.await() }
        check(!closing && !exited.isCompleted) { "Native engine stopped during startup" }
    }

    suspend fun start(configuration: Configuration) {
        connect(launchService = !serviceStarted)
        if (state == EngineProtocol.STATE_IDLE && !startSent) {
            require(configuration.args.isNotEmpty()) { "Proxy arguments are empty" }
            setEndpoint(configuration.host, configuration.port)
            startSent = true
            portReleased = false
            saveEndpoint()
            send(EngineProtocol.START, Bundle().apply {
                putStringArray(EngineProtocol.ARGS, configuration.args.toTypedArray())
                putString(EngineProtocol.HOST, configuration.host)
                putInt(EngineProtocol.PORT, configuration.port)
                putString(EngineProtocol.DNS, configuration.dns)
                putBoolean(EngineProtocol.IPV6, configuration.ipv6)
                putString(EngineProtocol.APP_LIST_TYPE, configuration.appListType)
                putStringArray(EngineProtocol.APPS, configuration.apps.toTypedArray())
            })
        }
        awaitStarted()
    }

    suspend fun awaitConfigured() {
        configured.await()
    }

    suspend fun awaitStarted() {
        withTimeout(15_000) { started.await() }
        check(!closing && !exited.isCompleted) { failure ?: "Native engine stopped during startup" }
    }

    suspend fun awaitExit() {
        exited.await()
        throw IllegalStateException(failure ?: "Native engine process exited")
    }

    suspend fun awaitStopped() {
        awaitProcessExit()
        awaitPortRelease()
    }

    suspend fun stop() {
        closing = true
        if (!exited.isCompleted) {
            findPid()
            sendStop()
            if (withTimeoutOrNull(2_000) { awaitProcessExit(); true } == null) {
                Log.w(TAG, "Native engine did not stop in time, killing process $pid")
                abort()
            }
        }
        withTimeout(5_000) { awaitStopped() }
        unbind()
    }

    private suspend fun awaitProcessExit() {
        while (!exited.isCompleted) {
            if (forceStopping) abort()
            if (!processAlive()) onExit() else delay(20)
        }
    }

    private fun sendStop() {
        try {
            if (remote != null) send(EngineProtocol.STOP)
        } catch (e: Exception) {
            Log.w(TAG, "Could not request native engine stop", e)
        }
    }

    private fun setEndpoint(host: String, port: Int) {
        require(port in 1..65535) { "Invalid proxy port" }
        val address = host.removeSurrounding("[", "]")
        proxyAddress = requireNotNull(Os.inet_pton(OsConstants.AF_INET, address)
            ?: Os.inet_pton(OsConstants.AF_INET6, address)) { "Invalid proxy address" }
        proxyPort = port
    }

    private fun saveEndpoint() {
        savedSession.edit()
            .putInt(EngineProtocol.PID, pid)
            .putString(EngineProtocol.HOST, proxyAddress?.hostAddress)
            .putInt(EngineProtocol.PORT, proxyPort)
            .commit()
    }

    private suspend fun awaitPortRelease() {
        val address = proxyAddress ?: return
        while (!portReleased) {
            val available = withContext(Dispatchers.IO) {
                try {
                    ServerSocket().use { socket ->
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress(address, proxyPort))
                        true
                    }
                } catch (e: BindException) {
                    !address.isAnyLocalAddress && NetworkInterface.getByInetAddress(address) == null
                }
            }
            if (available) portReleased = true else delay(20)
        }
    }

    fun abort() {
        closing = true
        forceStopping = true
        if (exited.isCompleted) return
        findPid()
        if (pid <= 0 && serviceStarted) {
            cancelUnconnectedStart()
            if (pid <= 0) return
        }
        unbind()
        if (pid > 0 && pid != Process.myPid() && processAlive()) {
            Process.killProcess(pid)
        } else {
            onExit()
        }
    }

    fun detach() {
        unlink()
        unbind()
        binder = null
        remote = null
    }

    private fun findPid() {
        if (pid > 0) return
        pid = running(application)?.takeIf { it.first == mode }?.second ?: 0
    }

    @Suppress("DEPRECATION")
    private fun cancelUnconnectedStart() {
        application.stopService(Intent(application, serviceClass(mode)))
        val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val process = manager.runningAppProcesses.orEmpty().firstOrNull {
            it.uid == Process.myUid() && it.processName == "${application.packageName}:native"
        }
        if (process != null && process.pid > 0) {
            pid = process.pid
            return
        }
        val pending = manager.getRunningServices(Int.MAX_VALUE).any {
            it.uid == Process.myUid() && it.service.className == serviceClass(mode).name
        }
        if (!pending && process == null) onExit()
    }

    private fun processAlive(): Boolean {
        if (exited.isCompleted) return false
        if (binder?.isBinderAlive == false) return false
        findPid()
        if (pid <= 0) return serviceStarted
        if (binder == null) {
            val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (manager.runningAppProcesses.orEmpty().none {
                    it.pid == pid && it.uid == Process.myUid() && it.processName == "${application.packageName}:native"
                }) return false
        }
        return try {
            Os.kill(pid, 0)
            true
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.ESRCH || e.errno == OsConstants.EPERM) false else throw e
        }
    }

    private fun send(what: Int, data: Bundle = Bundle()) {
        val message = Message.obtain(null, what)
        message.replyTo = replies
        message.data = data
        checkNotNull(remote).send(message)
    }

    private fun onExit() {
        if (exited.isCompleted) return
        val error = IllegalStateException(failure ?: "Native engine process exited")
        unlink()
        unbind()
        exited.complete(Unit)
        hello.completeExceptionally(error)
        configured.completeExceptionally(error)
        started.completeExceptionally(error)
        if (pid > 0 && savedSession.getInt(EngineProtocol.PID, 0) == pid) {
            savedSession.edit().clear().apply()
        }
    }

    private fun unbind() {
        if (!bound) return
        bound = false
        application.unbindService(connection)
    }

    private fun unlink() {
        try {
            binder?.unlinkToDeath(deathRecipient, 0)
        } catch (_: NoSuchElementException) {
        }
    }
}
