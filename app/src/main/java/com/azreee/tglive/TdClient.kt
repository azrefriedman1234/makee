package com.azreee.tglive

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class TdClient(
    private val filesDir: File,
    private val apiId: Int,
    private val apiHash: String
) {
    private val clientRef = AtomicReference<Client?>(null)

    var onAuthState: ((TdApi.AuthorizationState) -> Unit)? = null
    var onNewMessage: ((TdApi.Message) -> Unit)? = null
    var onFileReady: ((TdApi.File) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start() {
        Client.execute(TdApi.SetLogVerbosityLevel(1))

        val handler = Client.ResultHandler { obj ->
            when (obj) {
                is TdApi.UpdateAuthorizationState -> onAuthState?.invoke(obj.authorizationState)
                is TdApi.UpdateNewMessage -> onNewMessage?.invoke(obj.message)
                is TdApi.UpdateFile -> {
                    val p = obj.file.local?.path
                    val done = obj.file.local?.isDownloadingCompleted == true
                    if (done && !p.isNullOrBlank()) onFileReady?.invoke(obj.file)
                }
            }
        }

        val client = Client.create(handler, null, null)
        clientRef.set(client)

        val dbDir = File(filesDir, "tdlib_db").apply { mkdirs() }
        val fDir = File(filesDir, "tdlib_files").apply { mkdirs() }

        send(TdApi.SetTdlibParameters().apply {
            databaseDirectory = dbDir.absolutePath
            filesDirectory = fDir.absolutePath
            useMessageDatabase = true
            useFileDatabase = true
            useChatInfoDatabase = true
            useSecretChats = false
            apiId = this@TdClient.apiId
            apiHash = this@TdClient.apiHash
            systemLanguageCode = "he"
            deviceModel = android.os.Build.MODEL
            systemVersion = android.os.Build.VERSION.RELEASE
            applicationVersion = "1.1"
            enableStorageOptimizer = true
        })

        send(TdApi.CheckDatabaseEncryptionKey())
    }

    fun setPhone(phone: String) = send(TdApi.SetAuthenticationPhoneNumber(phone, null))
    fun setCode(code: String) = send(TdApi.CheckAuthenticationCode(code))
    fun setPassword(pass: String) = send(TdApi.CheckAuthenticationPassword(pass))

    fun downloadFile(fileId: Int, priority: Int = 64) =
        send(TdApi.DownloadFile(fileId, priority, 0, 0, true))

    fun searchPublicChat(usernameOrAt: String, onResult: (Long?) -> Unit) {
        val u = usernameOrAt.trim().removePrefix("@")
        sendWithResult(TdApi.SearchPublicChat(u)) { obj ->
            if (obj is TdApi.Chat) onResult(obj.id) else onResult(null)
        }
    }

    fun sendProcessedToChannel(targetChatId: Long, localPath: String, caption: String = "") {
        val input = TdApi.InputFileLocal(localPath)
        val content =
            if (localPath.endsWith(".jpg", true) || localPath.endsWith(".jpeg", true) || localPath.endsWith(".png", true)) {
                TdApi.InputMessagePhoto(
                    input, null, intArrayOf(), 0, 0,
                    TdApi.FormattedText(caption, null), null, false
                )
            } else {
                TdApi.InputMessageVideo(
                    input, null, intArrayOf(), 0, 0, 0,
                    false, false, TdApi.FormattedText(caption, null), null, false
                )
            }
        send(TdApi.SendMessage(targetChatId, 0, 0, null, null, content))
    }

    private fun send(q: TdApi.Function) {
        try {
            clientRef.get()?.send(q) { /* ignore */ }
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "send error")
        }
    }

    private fun sendWithResult(q: TdApi.Function, cb: (TdApi.Object) -> Unit) {
        try {
            clientRef.get()?.send(q) { obj -> cb(obj) }
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "send error")
        }
    }
}
