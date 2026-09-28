package io.github.romanvht.byedpi.core

object TProxyService {
    init {
        System.loadLibrary("byedpi")
    }

    external fun startTunnel(config: String, fd: Int): Int

    external fun stopTunnel()
}
