package io.github.romanvht.byedpi.services

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.utility.createPauseNotification
import io.github.romanvht.byedpi.utility.registerNotificationChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

object ServiceManager {
    private const val TAG = "ServiceManager"
    private const val PAUSE_NOTIFICATION_ID = 3
    private val scope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler {
            _, error -> Log.e(TAG, "Service command failed", error)
        }
    )
    private val commands = AtomicLong()
    private val mutex = Mutex()
    private val failures = MutableStateFlow(0L)
    private val stopEvents = MutableStateFlow(0L)
    val engineFailure: StateFlow<Long> = failures.asStateFlow()
    val stopRequests: StateFlow<Long> = stopEvents.asStateFlow()
    private var application: Application? = null
    private var session: Session? = null

    private class Session(val mode: Mode, val engine: NativeEngine) {
        val configuration = CompletableDeferred<Configuration>()
        val configured = CompletableDeferred<Boolean>()
        val started = CompletableDeferred<Boolean>()
        val stopped = CompletableDeferred<Unit>()
        var stopping = false
        var job: Job? = null
        var failureReported = false
    }

    fun refresh(context: Context) {
        val app = context.applicationContext as Application
        scope.launch { restore(app) }
    }

    private fun restore(app: Application) {
        application = app
        if (session != null) return
        val running = NativeEngine.running(app)
        if (running == null) {
            if (appStatus.first != AppStatus.Halted) publish(appStatus.second, AppStatus.Halted, STOPPED_BROADCAST)
            return
        }
        val current = Session(running.first, NativeEngine(app, running.first, running.second))
        session = current
        publish(current.mode, AppStatus.Running, STARTED_BROADCAST)
        monitor(current, launchService = false)
    }

    fun start(context: Context, mode: Mode) {
        val app = context.applicationContext as Application
        val command = commands.incrementAndGet()
        scope.launch { start(app, mode, command) }
    }

    suspend fun startAndAwait(context: Context, mode: Mode): Boolean {
        val app = context.applicationContext as Application
        val command = commands.incrementAndGet()
        return withContext(Dispatchers.Main.immediate) { start(app, mode, command) }
    }

    private suspend fun start(
        app: Application,
        mode: Mode,
        command: Long,
        configured: CompletableDeferred<Boolean>? = null,
    ): Boolean = mutex.withLock {
        if (commands.get() != command) return@withLock false
        restore(app)
        session?.let { previous ->
            if (!previous.stopping && previous.mode == mode) {
                configure(previous, app)
                configured?.complete(previous.configured.await())
                return@withLock previous.started.await()
            }
            requestStop(previous)
            previous.stopped.await()
        }
        if (commands.get() != command) return@withLock false

        val current = Session(mode, NativeEngine(app, mode))
        session = current
        configure(current, app)
        publish(mode, AppStatus.Running, STARTED_BROADCAST)
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(PAUSE_NOTIFICATION_ID)
        monitor(current, launchService = true)
        try {
            configured?.complete(current.configured.await())
            current.started.await()
        } catch (e: CancellationException) {
            requestStop(current)
            throw e
        }
    }

    private fun configure(current: Session, app: Application) {
        if (current.configuration.isCompleted || current.started.isCompleted) return
        try {
            current.configuration.complete(NativeEngine.configuration(app))
        } catch (e: Exception) {
            current.configuration.completeExceptionally(e)
        }
    }

    private fun monitor(current: Session, launchService: Boolean) {
        current.engine.onStopping = {
            if (session === current && !current.stopping) stop()
        }
        current.job = scope.launch(start = CoroutineStart.LAZY) {
            var failed = false
            try {
                current.engine.connect(launchService)
                coroutineScope {
                    launch {
                        current.engine.awaitConfigured()
                        current.configured.complete(true)
                    }
                    val ready = async { current.engine.awaitStarted() }
                    try {
                        if (current.engine.needsConfiguration) {
                            select {
                                ready.onAwait { }
                                current.configuration.onAwait { current.engine.start(it) }
                            }
                        } else {
                            ready.await()
                        }
                    } finally {
                        ready.cancel()
                    }
                }
                if (!current.stopping) {
                    publish(current.mode, AppStatus.Running, STARTED_BROADCAST)
                    current.started.complete(true)
                }
                current.engine.awaitExit()
            } catch (e: CancellationException) {
                failed = !current.stopping
            } catch (e: Exception) {
                failed = !current.stopping
                if (failed) Log.e(TAG, "Native service failed", e)
            } finally {
                current.configured.complete(false)
                withContext(NonCancellable) {
                    try {
                        current.engine.stop()
                        finish(current, failed && !current.stopping)
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not confirm native process exit", e)
                        current.started.complete(false)
                        current.stopped.completeExceptionally(e)
                        failures.update { it + 1 }
                        current.failureReported = true
                        publish(current.mode, AppStatus.Halted, FAILED_BROADCAST)
                        scope.launch {
                            current.engine.awaitStopped()
                            finish(current, failed = true)
                        }
                    }
                }
            }
        }
        current.job?.start()
    }

    fun stop() {
        commands.incrementAndGet()
        stopEvents.update { it + 1 }
        scope.launch {
            application?.let { restore(it) }
            session?.let { requestStop(it) }
        }
    }

    suspend fun stopAndAwait() {
        commands.incrementAndGet()
        withContext(Dispatchers.Main.immediate) {
            application?.let { restore(it) }
            session?.let {
                requestStop(it)
                it.stopped.await()
            }
        }
    }

    fun restart(context: Context, mode: Mode) {
        val app = context.applicationContext as Application
        val command = commands.incrementAndGet()
        scope.launch {
            restore(app)
            session?.let {
                requestStop(it)
                it.stopped.await()
            }
            if (commands.get() == command) start(app, mode, command)
        }
    }

    suspend fun handleAction(context: Context, action: String, mode: Mode, sourcePid: Int = 0) {
        val app = context.applicationContext as Application
        withContext(Dispatchers.Main.immediate) {
            restore(app)
            val activePid = session?.engine?.processId?.takeIf { it > 0 } ?: NativeEngine.running(app)?.second
            if (sourcePid > 0 && sourcePid != activePid) return@withContext
            if (sourcePid > 0 && (action == STOP_ACTION || action == PAUSE_ACTION) && session?.mode != mode) {
                return@withContext
            }
            when (action) {
                START_ACTION, RESUME_ACTION, RESTART_ACTION -> {
                    if (mode != Mode.VPN || VpnService.prepare(app) == null) {
                        val configured = CompletableDeferred<Boolean>()
                        val command = commands.incrementAndGet()
                        val startup = scope.launch {
                            try {
                                if (action == RESTART_ACTION) {
                                    session?.let {
                                        requestStop(it)
                                        it.stopped.await()
                                    }
                                }
                                start(app, mode, command, configured)
                            } finally {
                                configured.complete(false)
                            }
                        }
                        if (withTimeoutOrNull(8_000) { configured.await() } != true) {
                            startup.cancel()
                            if (commands.get() == command) {
                                stop()
                                session?.engine?.abort()
                            }
                        }
                    } else if (session?.mode == mode) {
                        stop()
                        stopAndAwait()
                    }
                }
                STOP_ACTION, PAUSE_ACTION -> {
                    val activeMode = session?.mode ?: mode
                    stop()
                    stopAndAwait()
                    if (session != null) return@withContext
                    val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (action == PAUSE_ACTION) {
                        val channel = if (activeMode == Mode.VPN) "ByeDPIVpn" else "ByeDPI Proxy"
                        val name = if (activeMode == Mode.VPN) R.string.vpn_channel_name else R.string.proxy_channel_name
                        registerNotificationChannel(app, channel, name)
                        notifications.notify(PAUSE_NOTIFICATION_ID, createPauseNotification(
                            app, channel, R.string.notification_title, R.string.service_paused_text, activeMode,
                        ))
                    } else {
                        notifications.cancel(PAUSE_NOTIFICATION_ID)
                    }
                }
            }
        }
    }

    private fun requestStop(current: Session) {
        if (current.stopping) return
        current.stopping = true
        current.started.complete(false)
        current.job?.cancel()
    }

    private fun finish(current: Session, failed: Boolean) {
        current.engine.onStopping = null
        if (session === current) {
            session = null
            if (failed && !current.failureReported) failures.update { it + 1 }
            publish(current.mode, AppStatus.Halted, if (failed) FAILED_BROADCAST else STOPPED_BROADCAST)
        }
        current.started.complete(false)
        current.stopped.complete(Unit)
    }

    private fun publish(mode: Mode, status: AppStatus, action: String) {
        setStatus(status, mode)
        val sender = if (mode == Mode.VPN) Sender.VPN else Sender.Proxy
        application?.let {
            it.sendBroadcast(Intent(action).setPackage(it.packageName).putExtra(SENDER, sender.ordinal))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) QuickTileService.updateTile()
    }
}
