package io.github.dovecoteescapee.byedpi.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    private val mutex = Mutex()

    suspend fun startProxy(preferences: ByeDpiProxyPreferences): Int {
        val result = createSocket(preferences)

        if (result < 0) {
            return -1
        }

        return jniStartProxy()
    }

    suspend fun stopProxy(): Int {
        mutex.withLock {
            val result = jniStopProxy()

            if (result < 0) {
                return -1
            }

            return result
        }
    }

    private suspend fun createSocket(preferences: ByeDpiProxyPreferences): Int =
        mutex.withLock {
            val result = createSocketFromPreferences(preferences)

            if (result < 0) {
                return -1
            }

            return result
        }

    private fun createSocketFromPreferences(preferences: ByeDpiProxyPreferences) =
        when (preferences) {
            is ByeDpiProxyCmdPreferences -> jniCreateSocket(preferences.args)
            is ByeDpiProxyUIPreferences -> jniCreateSocket(preferences.uiargs)
        }

    private external fun jniCreateSocket(args: Array<String>): Int
    private external fun jniStartProxy(): Int
    private external fun jniStopProxy(): Int
}
