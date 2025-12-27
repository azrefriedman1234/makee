package com.azreee.tglive

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

object BlurEngine {

    private fun normToPx(rect: NormRect, w: Int, h: Int): IntArray {
        val x = (rect.x * w).toInt().coerceAtLeast(0)
        val y = (rect.y * h).toInt().coerceAtLeast(0)
        val rw = (rect.w * w).toInt().coerceAtLeast(2)
        val rh = (rect.h * h).toInt().coerceAtLeast(2)
        return intArrayOf(x, y, rw, rh)
    }

    fun blurVideo(inputPath: String, outputPath: String, videoW: Int, videoH: Int, rects: List<NormRect>): Boolean {
        val pxRects = rects.map { normToPx(it, videoW, videoH) }
        val filters = mutableListOf<String>()
        val overlays = mutableListOf<Triple<Int, Int, Int>>()
        var idx = 1

        for (r in pxRects) {
            val (x,y,w,h) = r
            filters += "[0:v]crop=$w:$h:$x:$y,boxblur=10:1[blur$idx]"
            overlays += Triple(idx, x, y)
            idx++
        }

        var composed = "[0:v]"
        for ((i, x, y) in overlays) {
            filters += "$composed[blur$i]overlay=$x:$y[v$i]"
            composed = "[v$i]"
        }

        val vf = if (filters.isEmpty()) "null" else filters.joinToString(";")
        val outMap = if (overlays.isEmpty()) "0:v" else composed

        val cmd = listOf(
            "-y","-i", inputPath,
            "-filter_complex", vf,
            "-map", outMap,
            "-map", "0:a?",
            "-c:v","libx264","-crf","24","-preset","veryfast",
            "-c:a","aac","-b:a","128k",
            outputPath
        ).joinToString(" ")

        val session = FFmpegKit.execute(cmd)
        return ReturnCode.isSuccess(session.returnCode)
    }

    fun blurImage(inputPath: String, outputPath: String, imageW: Int, imageH: Int, rects: List<NormRect>): Boolean {
        val pxRects = rects.map { normToPx(it, imageW, imageH) }
        val filters = mutableListOf<String>()
        val overlays = mutableListOf<Triple<Int, Int, Int>>()
        var idx = 1

        for (r in pxRects) {
            val (x,y,w,h) = r
            filters += "[0:v]crop=$w:$h:$x:$y,boxblur=10:1[blur$idx]"
            overlays += Triple(idx, x, y)
            idx++
        }

        var composed = "[0:v]"
        for ((i, x, y) in overlays) {
            filters += "$composed[blur$i]overlay=$x:$y[v$i]"
            composed = "[v$i]"
        }

        val vf = if (filters.isEmpty()) "null" else filters.joinToString(";")
        val outMap = if (overlays.isEmpty()) "0:v" else composed

        val cmd = listOf(
            "-y","-i", inputPath,
            "-filter_complex", vf,
            "-map", outMap,
            "-frames:v","1",
            outputPath
        ).joinToString(" ")

        val session = FFmpegKit.execute(cmd)
        return ReturnCode.isSuccess(session.returnCode)
    }
}
