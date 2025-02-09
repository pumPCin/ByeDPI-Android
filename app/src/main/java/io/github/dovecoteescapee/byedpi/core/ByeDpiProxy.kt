package io.github.dovecoteescapee.byedpi.core

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    private val mutex = Mutex()
    private var fd = -1

    suspend fun startProxy(preferences: ByeDpiProxyPreferences): Int {
        val fd = createSocket(preferences)
        if (fd < 0) return -1

        return jniStartProxy(fd)
    }

    suspend fun stopProxy(): Int {
        mutex.withLock {
            if (fd < 0) throw IllegalStateException("Proxy is not running")

            val result = jniStopProxy(fd)
            fd = -1

            return result
        }
    }

    private suspend fun createSocket(preferences: ByeDpiProxyPreferences): Int =
        mutex.withLock {
            if (fd >= 0) throw IllegalStateException("Proxy is already running")

            val result = createSocketFromPreferences(preferences)
            if (result < 0) return -1
            fd = result

            return result
        }

    private fun createSocketFromPreferences(preferences: ByeDpiProxyPreferences) =
        when (preferences) {
            is ByeDpiProxyCmdPreferences -> jniCreateSocket(preferences.args)
            is ByeDpiProxyUIPreferences -> jniCreateSocket(buildCommandLineArgs(preferences))
        }

    private fun buildCommandLineArgs(preferences: ByeDpiProxyUIPreferences): Array<String> {
        val args = mutableListOf("ciadpi")

        preferences.ip.takeIf { it.isNotEmpty() }?.let {
            args.add("-i${it}")
        }

        preferences.port.takeIf { it != 0 }?.let {
            args.add("-p${it}")
        }

        preferences.maxConnections.takeIf { it != 0 }?.let {
            args.add("-c${it}")
        }

        preferences.bufferSize.takeIf { it != 0 }?.let {
            args.add("-b${it}")
        }

        val protocols = mutableListOf<String>()
        if (preferences.desyncHttps) protocols.add("t")
        if (preferences.desyncHttp) protocols.add("h")

        if (!preferences.hosts.isNullOrBlank()) {
            val hostStr = ":${preferences.hosts}"
            val hostBlock = mutableListOf<String>()

            when (preferences.hostsMode) {
                ByeDpiProxyUIPreferences.HostsMode.Blacklist -> {
                    hostBlock.add("-H${hostStr}")
                    hostBlock.add("-An")
                    if (protocols.isNotEmpty()) {
                        hostBlock.add("-K${protocols.joinToString(",")}")
                    }
                }
                ByeDpiProxyUIPreferences.HostsMode.Whitelist -> {
                    if (protocols.isNotEmpty()) {
                        hostBlock.add("-K${protocols.joinToString(",")}")
                    }
                    hostBlock.add("-H${hostStr}")
                }
                else -> {}
            }
            args.addAll(hostBlock)
        } else {
            if (protocols.isNotEmpty()) {
                args.add("-K${protocols.joinToString(",")}")
            }
        }

        preferences.defaultTtl.takeIf { it != 0 }?.let {
            args.add("-g${it}")
        }

        if (preferences.noDomain) {
            args.add("-N")
        }

        preferences.desyncMethod.let { method ->
            preferences.splitPosition.takeIf { it != 0 }?.let { pos ->
                var posArg = pos.toString()
                if (preferences.splitAtHost) {
                    posArg += "+h"
                }
                val option = when (method) {
                    ByeDpiProxyUIPreferences.DesyncMethod.Split -> "-s"
                    ByeDpiProxyUIPreferences.DesyncMethod.Disorder -> "-d"
                    ByeDpiProxyUIPreferences.DesyncMethod.OOB -> "-o"
                    ByeDpiProxyUIPreferences.DesyncMethod.DISOOB -> "-q"
                    ByeDpiProxyUIPreferences.DesyncMethod.Fake -> "-f"
                    ByeDpiProxyUIPreferences.DesyncMethod.None -> ""
                }
                args.add("${option}${posArg}")
            }
        }

        if (preferences.desyncMethod == ByeDpiProxyUIPreferences.DesyncMethod.Fake) {
            preferences.fakeTtl.takeIf { it != 0 }?.let {
                args.add("-t${it}")
            }

            preferences.fakeSni.takeIf { it.isNotEmpty() }?.let {
                args.add("-n${it}")
            }
        }

        if (preferences.desyncMethod == ByeDpiProxyUIPreferences.DesyncMethod.OOB ||
            preferences.desyncMethod == ByeDpiProxyUIPreferences.DesyncMethod.DISOOB) {
            args.add("-e${preferences.oobChar}")
        }

        preferences.fakeOffset.takeIf { it != 0 }?.let {
            args.add("-O${it}")
        }

        val modHttpFlags = mutableListOf<String>()
        if (preferences.hostMixedCase) modHttpFlags.add("h")
        if (preferences.domainMixedCase) modHttpFlags.add("d")
        if (preferences.hostRemoveSpaces) modHttpFlags.add("r")
        if (modHttpFlags.isNotEmpty()) {
            args.add("-M${modHttpFlags.joinToString(",")}")
        }

        if (preferences.tlsRecordSplit) {
            preferences.tlsRecordSplitPosition.takeIf { it != 0 }?.let {
                var tlsRecArg = it.toString()
                if (preferences.tlsRecordSplitAtSni) {
                    tlsRecArg += "+s"
                }
                args.add("-r${tlsRecArg}")
            }
        }

        if (preferences.tcpFastOpen) {
            args.add("-F")
        }

        if (preferences.dropSack) {
            args.add("-Y")
        }

        args.add("-An")

        if (preferences.desyncUdp) {
            args.add("-Ku")

            preferences.udpFakeCount.takeIf { it != 0 }?.let {
                args.add("-a${it}")
            }

            args.add("-An")
        }

        Log.d("ByeDpiProxy", "UI to cmd: ${args.joinToString(" ")}")
        return args.toTypedArray()
    }

    private external fun jniCreateSocket(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
}
