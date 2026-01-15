package io.github.romanvht.byedpi.services

import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode

var appStatus = AppStatus.Halted to Mode.VPN
    private set

fun setStatus(status: AppStatus, mode: Mode) {
    appStatus = status to mode
}
