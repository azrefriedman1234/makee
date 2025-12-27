package com.azreee.tglive

import org.drinkless.tdlib.TdApi

data class UiMessage(
    val chatId: Long,
    val messageId: Long,
    val date: Long,
    val rawText: String,
    val textHe: String,
    val hasMedia: Boolean,
    val content: TdApi.MessageContent?
)

data class NormRect(val x: Float, val y: Float, val w: Float, val h: Float) // normalized 0..1

/** Collect any TDLib file IDs referenced by this message (thumb + main media). */
fun UiMessage.fileIds(): List<Int> {
    val out = mutableListOf<Int>()
    val c = content ?: return out
    when (c) {
        is TdApi.MessagePhoto -> {
            val sizes = c.photo?.sizes
            if (!sizes.isNullOrEmpty()) {
                val best = sizes.maxByOrNull { it.photo?.size ?: 0 }
                best?.photo?.id?.let(out::add)
                best?.photo?.local?.path?.let { /* path handled elsewhere */ }
            }
        }
        is TdApi.MessageVideo -> {
            c.video?.video?.id?.let(out::add)
            c.video?.thumbnail?.file?.id?.let(out::add)
        }
        is TdApi.MessageAnimation -> {
            c.animation?.animation?.id?.let(out::add)
            c.animation?.thumbnail?.file?.id?.let(out::add)
        }
        is TdApi.MessageDocument -> {
            c.document?.document?.id?.let(out::add)
            c.document?.thumbnail?.file?.id?.let(out::add)
        }
        else -> {
            // ignore
        }
    }
    return out.distinct()
}
