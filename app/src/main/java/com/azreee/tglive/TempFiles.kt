package com.azreee.tglive

import android.content.Context
import java.io.File

/**
 * Centralized temp-files helper.
 *
 * We keep all app-generated outputs under filesDir/pasiflonet_temp
 * and TDLib downloads under filesDir/tdlib_files.
 */
object TempFiles {
    private const val TEMP_DIR_NAME = "pasiflonet_temp"
    private const val TDLIB_FILES_DIR_NAME = "tdlib_files"

    fun tempDir(context: Context): File {
        return File(context.filesDir, TEMP_DIR_NAME).apply { mkdirs() }
    }

    fun cleanupAll(context: Context) {
        // Our processed outputs
        runCatching { tempDir(context).deleteRecursively() }

        // TDLib downloaded media cache
        runCatching { File(context.filesDir, TDLIB_FILES_DIR_NAME).deleteRecursively() }
    }

    fun cleanupForMessage(context: Context, messageId: Long) {
        // Delete processed outputs for that message
        val dir = tempDir(context)
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("m_${messageId}_")) {
                runCatching { f.delete() }
            }
        }
    }
}
