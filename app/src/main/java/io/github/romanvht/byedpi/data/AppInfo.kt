package io.github.romanvht.byedpi.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    var isSelected: Boolean,
    var icon: Drawable? = null
)