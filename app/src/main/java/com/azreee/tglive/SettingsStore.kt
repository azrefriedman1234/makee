package com.azreee.tglive

import android.content.Context

data class AppSettings(
    val apiId: Int,
    val apiHash: String,
    val channel: String,
    val libreUrl: String
)

object SettingsStore {
    private const val PREF = "azreee_settings"
    private const val K_API_ID = "api_id"
    private const val K_API_HASH = "api_hash"
    private const val K_CH = "channel"
    private const val K_LIBRE = "libre"

    fun load(ctx: Context): AppSettings {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return AppSettings(
            apiId = sp.getInt(K_API_ID, 0),
            apiHash = sp.getString(K_API_HASH, "") ?: "",
            channel = sp.getString(K_CH, "@") ?: "@",
            libreUrl = sp.getString(K_LIBRE, Translate.defaultLibreUrl) ?: Translate.defaultLibreUrl
        )
    }

    fun save(ctx: Context, s: AppSettings) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(K_API_ID, s.apiId)
            .putString(K_API_HASH, s.apiHash)
            .putString(K_CH, s.channel)
            .putString(K_LIBRE, s.libreUrl)
            .apply()
    }
}
