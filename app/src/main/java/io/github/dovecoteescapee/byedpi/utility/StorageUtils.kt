package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object StorageUtils {
    fun getRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(StorageManager::class.java)
            val volumes = manager.storageVolumes
            for (volume in volumes) {
                if (volume.isPrimary) addRoot(roots, volume.directory)
            }
            for (volume in volumes) {
                if (!volume.isPrimary) addRoot(roots, volume.directory)
            }
        } else {
            addRoot(roots, Environment.getExternalStorageDirectory())
            val externalDirectories = context.getExternalFilesDirs(null)
            for (directory in externalDirectories) {
                if (directory == null) continue

                val marker = "${File.separator}Android${File.separator}"
                val rootPath = directory.absolutePath.substringBefore(marker)
                addRoot(roots, File(rootPath))
            }
        }

        if (roots.isEmpty()) {
            addRoot(roots, Environment.getExternalStorageDirectory())
        }

        return roots
    }

    fun isInside(file: File, root: File): Boolean {
        return try {
            val filePath = file.canonicalPath
            val rootPath = root.canonicalPath
            filePath == rootPath || filePath.startsWith(rootPath + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    private fun addRoot(roots: MutableList<File>, root: File?) {
        if (root == null || !root.exists() || !root.canRead()) return

        for (existingRoot in roots) {
            if (existingRoot.absolutePath == root.absolutePath) return
        }

        roots.add(root)
    }
}
