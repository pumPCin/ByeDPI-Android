package io.github.dovecoteescapee.byedpi.core

object TProxyService {
    init {
        System.loadLibrary("byedpi")
    }

    external fun startTunnel(config: String, fd: Int): Int

    external fun stopTunnel()
}
