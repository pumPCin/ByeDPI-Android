package io.github.romanvht.byedpi.data

import io.github.romanvht.byedpi.BuildConfig

const val STARTED_BROADCAST = "${BuildConfig.APPLICATION_ID}.STARTED"
const val STOPPED_BROADCAST = "${BuildConfig.APPLICATION_ID}.STOPPED"
const val FAILED_BROADCAST = "${BuildConfig.APPLICATION_ID}.FAILED"

const val SENDER = "sender"

enum class Sender(val senderName: String) {
    Proxy("Proxy"),
    VPN("VPN")
}