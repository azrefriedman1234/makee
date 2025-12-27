package com.azreee.tglive

import com.arthenica.ffmpegkit.FFprobeKit

object MediaInfo {
    data class Dim(val w: Int, val h: Int)

    fun getDimensions(path: String): Dim? = try {
        val info = FFprobeKit.getMediaInformation(path).mediaInformation
        val v = info?.streams?.firstOrNull { it.type == "video" }
        val w = v?.width
        val h = v?.height
        if (w != null && h != null) Dim(w, h) else null
    } catch (_: Exception) { null }
}
