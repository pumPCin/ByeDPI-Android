package io.github.romanvht.byedpi.utility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import io.github.romanvht.byedpi.R

object ClipboardUtils {
    fun copy(context: Context, text: String, label: String = "text", showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)

        if (showToast) {
            Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun paste(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip

        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString()
        } else {
            null
        }
    }
}