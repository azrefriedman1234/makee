package com.azreee.tglive

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        // Clear temporary files when the app is closed.
        TempFiles.cleanupAll(this)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    AppRoot(filesDir)
                }
            }
        }
    }
}

private const val MAX_FEED_MESSAGES = 120

@Composable
private fun AppRoot(filesDir: File) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(SettingsStore.load(ctx)) }
    var showSettings by remember { mutableStateOf(settings.apiId == 0 || settings.apiHash.isBlank()) }

    // TD client lifecycle
    var td by remember { mutableStateOf<TdClient?>(null) }
    var tdKey by remember { mutableStateOf(0) } // force recreate

    LaunchedEffect(settings.apiId, settings.apiHash, tdKey) {
        td = if (settings.apiId != 0 && settings.apiHash.isNotBlank()) {
            TdClient(filesDir, settings.apiId, settings.apiHash).also { it.start() }
        } else null
    }

    if (showSettings) {
        SettingsScreen(settings = settings) { newS ->
            SettingsStore.save(ctx, newS)
            settings = newS
            showSettings = false
            tdKey++
        }
        return
    }

    val client = td
    if (client == null) {
        SettingsScreen(settings = settings) { newS ->
            SettingsStore.save(ctx, newS)
            settings = newS
            showSettings = false
            tdKey++
        }
        return
    }

    App(td = client, filesDir = filesDir, settings = settings, onOpenSettings = { showSettings = true })
}

@Composable
private fun App(td: TdClient, filesDir: File, settings: AppSettings, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()

    var auth by remember { mutableStateOf<TdApi.AuthorizationState?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    var channelInput by remember { mutableStateOf(settings.channel) }
    var channelId by remember { mutableStateOf<Long?>(null) }
    var channelStatus by remember { mutableStateOf("") }

    var libreUrl by remember { mutableStateOf(settings.libreUrl) }

    val feed = remember { mutableStateListOf<UiMessage>() }
    var selected by remember { mutableStateOf<UiMessage?>(null) }

    val filePathById = remember { mutableStateMapOf<Int, String>() }

    DisposableEffect(td) {
        td.onAuthState = { s -> auth = s }
        td.onError = { e -> lastError = e }
        td.onNewMessage = { m ->
            val raw = (m.content as? TdApi.MessageText)?.text?.text ?: ""
            val hasMedia = m.content is TdApi.MessagePhoto || m.content is TdApi.MessageVideo
            scope.launch(Dispatchers.IO) {
                val he = Translate.toHebrewIfNeeded(raw, libreUrl)
                val ui = UiMessage(
                    chatId = m.chatId,
                    messageId = m.id,
                    date = m.date.toLong(),
                    rawText = raw,
                    textHe = he,
                    hasMedia = hasMedia,
                    content = m.content
                )
                launch(Dispatchers.Main) {
                    feed.add(0, ui)
                    // Keep only 120 rows: newest at top, delete oldest (and its temp files).
                    while (feed.size > 120) {
                        val removed = feed.removeLast()
                        // Delete cached files for removed message (thumb/media + processed outputs).
                        TempFiles.deleteForMessage(
                            context = context,
                            messageId = removed.messageId,
                            fileIds = removed.fileIds()
                        )
                        // Also forget mapping for removed file IDs.
                        removed.fileIds().forEach { filePathById.remove(it) }
                    }
                }
            }
        }
        td.onFileReady = { f ->
            val p = f.local?.path
            if (!p.isNullOrBlank()) filePathById[f.id] = p
        }
        onDispose { }
    }

    val a = auth
    when (a) {
        is TdApi.AuthorizationStateWaitPhoneNumber -> LoginPhone(onSend = td::setPhone, lastError = lastError, onOpenSettings = onOpenSettings)
        is TdApi.AuthorizationStateWaitCode -> LoginCode(onSend = td::setCode, lastError = lastError, onOpenSettings = onOpenSettings)
        is TdApi.AuthorizationStateWaitPassword -> LoginPassword(onSend = td::setPassword, lastError = lastError, onOpenSettings = onOpenSettings)
        is TdApi.AuthorizationStateReady -> Home(
            feed = feed,
            selected = selected,
            onSelect = { selected = it },
            lastError = lastError,
            channelInput = channelInput,
            onChannelInput = { channelInput = it },
            onSaveChannel = {
                channelStatus = "מחפש ערוץ..."
                td.searchPublicChat(channelInput) { id ->
                    channelId = id
                    channelStatus = if (id != null) "✅ נשמר יעד (chatId=$id)" else "❌ לא נמצא"
                }
            },
            channelStatus = channelStatus,
            libreUrl = libreUrl,
            onLibreUrl = { libreUrl = it },
            onApplyLibre = { /* just keeps state */ },
            onOpenSettings = onOpenSettings,
            details = { msg ->
                MessageDetails(
                    td = td,
                    filesDir = filesDir,
                    msg = msg,
                    targetChannelId = channelId,
                    filePathById = filePathById
                )
            }
        )
        else -> Scaffold(
            topBar = { TopAppBar(title = { Text("טלגרם לייב") }, actions = { TextButton(onClick = onOpenSettings){ Text("הגדרות") } }) }
        ) { p ->
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("מתחבר... ${(a?.javaClass?.simpleName ?: "")}")
            }
        }
    }
}

@Composable
private fun Home(
    feed: List<UiMessage>,
    selected: UiMessage?,
    onSelect: (UiMessage) -> Unit,
    lastError: String?,
    channelInput: String,
    onChannelInput: (String) -> Unit,
    onSaveChannel: () -> Unit,
    channelStatus: String,
    libreUrl: String,
    onLibreUrl: (String) -> Unit,
    onApplyLibre: () -> Unit,
    onOpenSettings: () -> Unit,
    details: @Composable (UiMessage) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("טלגרם לייב", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onOpenSettings) { Text("הגדרות") }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = onChannelInput,
                            label = { Text("ערוץ יעד לשליחה (ציבורי) @...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onSaveChannel) { Text("שמור") }
                    }
                    if (channelStatus.isNotBlank()) Text(channelStatus)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = libreUrl,
                            onValueChange = onLibreUrl,
                            label = { Text("כתובת תרגום (LibreTranslate)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onApplyLibre) { Text("החל") }
                    }

                    if (lastError != null) Text(lastError, color = Color.Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    ) { p ->
        Row(Modifier.padding(p).fillMaxSize()) {
            Card(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Text("הודעות בלייב", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(feed) { m ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(m) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    text = m.textHe.ifBlank { "(אין טקסט)" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "מדיה: ${if (m.hasMedia) "כן" else "לא"} · chat ${m.chatId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Divider()
                        }
                    }
                }
            }

            Card(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                    if (selected == null) {
                        Text("בחר הודעה מהרשימה כדי לראות פרטים/מדיה")
                    } else {
                        details(selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginPhone(onSend: (String) -> Unit, lastError: String?, onOpenSettings: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("התחברות - טלפון") }, actions = { TextButton(onClick=onOpenSettings){Text("הגדרות")} }) }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("הכנס מספר כולל קידומת (לדוגמה +972...)")
            OutlinedTextField(phone, onValueChange = { phone = it }, label = { Text("מספר טלפון") }, singleLine = true)
            Button(onClick = { onSend(phone.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("שלח קוד") }
            if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
        }
    }
}

@Composable
private fun LoginCode(onSend: (String) -> Unit, lastError: String?, onOpenSettings: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("התחברות - קוד") }, actions = { TextButton(onClick=onOpenSettings){Text("הגדרות")} }) }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("הכנס את הקוד שהגיע מטלגרם")
            OutlinedTextField(code, onValueChange = { code = it }, label = { Text("קוד") }, singleLine = true)
            Button(onClick = { onSend(code.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("אמת") }
            if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
        }
    }
}

@Composable
private fun LoginPassword(onSend: (String) -> Unit, lastError: String?, onOpenSettings: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("התחברות - סיסמת 2FA") }, actions = { TextButton(onClick=onOpenSettings){Text("הגדרות")} }) }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("אם יש לך אימות דו-שלבי (2FA) בטלגרם – הכנס סיסמה")
            OutlinedTextField(pass, onValueChange = { pass = it }, label = { Text("סיסמה") }, singleLine = true)
            Button(onClick = { onSend(pass) }, modifier = Modifier.fillMaxWidth()) { Text("התחבר") }
            if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
        }
    }
}

@Composable
private fun MessageDetails(
    td: TdClient,
    filesDir: File,
    msg: UiMessage,
    targetChannelId: Long?,
    filePathById: Map<Int, String>
) {
    val scope = rememberCoroutineScope()

    var rects by remember { mutableStateOf<List<NormRect>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    var thumbPath by remember { mutableStateOf<String?>(null) }
    var mediaPath by remember { mutableStateOf<String?>(null) }
    var processedPath by remember { mutableStateOf<String?>(null) }

    val thumbFileId = remember(msg) {
        when (val c = msg.content) {
            is TdApi.MessagePhoto -> c.photo.sizes.lastOrNull()?.photo?.id
            is TdApi.MessageVideo -> c.video.thumbnail?.file?.id
            else -> null
        }
    }
    val mediaFileId = remember(msg) {
        when (val c = msg.content) {
            is TdApi.MessagePhoto -> c.photo.sizes.lastOrNull()?.photo?.id
            is TdApi.MessageVideo -> c.video.video?.id
            else -> null
        }
    }

    LaunchedEffect(filePathById, thumbFileId, mediaFileId) {
        if (thumbFileId != null) filePathById[thumbFileId]?.let { thumbPath = it }
        if (mediaFileId != null) filePathById[mediaFileId]?.let { mediaPath = it }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("פרטי הודעה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(msg.textHe.ifBlank { msg.rawText })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = thumbFileId != null, onClick = {
                status = "מוריד תמונה ממוזערת..."
                td.downloadFile(thumbFileId!!)
            }) { Text("הורד Thumbnail") }

            Button(enabled = mediaFileId != null, onClick = {
                status = "מוריד מדיה..."
                td.downloadFile(mediaFileId!!)
            }) { Text("הורד מדיה") }
        }

        val bmp = remember(thumbPath) {
            thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }

        if (bmp != null) {
            RectPickerOverlay(
                onRectsChanged = { rects = it },
                modifier = Modifier.fillMaxWidth().height(260.dp)
            ) {
                Image(bmp.asImageBitmap(), contentDescription = "thumb", modifier = Modifier.fillMaxSize())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("מלבנים: ${rects.size}", color = Color.Gray)
                TextButton(onClick = { rects = emptyList() }) { Text("נקה") }
            }
        } else {
            Text("אין Thumbnail עדיין. לחץ 'הורד Thumbnail'", color = Color.Gray)
        }

        val isVideo = msg.content is TdApi.MessageVideo
        val isPhoto = msg.content is TdApi.MessagePhoto

        Button(
            enabled = mediaPath != null && rects.isNotEmpty() && (isVideo || isPhoto),
            onClick = {
                scope.launch(Dispatchers.IO) {
                    status = "מעבד טשטוש..."
                    val inp = mediaPath!!
                    val dim = MediaInfo.getDimensions(inp)
                    if (dim == null) {
                        status = "לא הצלחתי לקרוא רזולוציה"
                        return@launch
                    }
                    val tempDir = TempFiles.tempDir(context)
                    val out = if (isPhoto)
                        File(tempDir, "m_${msg.messageId}_blur_${System.currentTimeMillis()}.jpg").absolutePath
                    else
                        File(tempDir, "m_${msg.messageId}_blur_${System.currentTimeMillis()}.mp4").absolutePath

                    val ok = if (isPhoto) BlurEngine.blurImage(inp, out, dim.w, dim.h, rects)
                             else BlurEngine.blurVideo(inp, out, dim.w, dim.h, rects)

                    status = if (ok) "✅ מוכן לשליחה" else "❌ כשל עיבוד"
                    if (ok) processedPath = out
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("החל טשטוש על המדיה") }

        Button(
            enabled = processedPath != null && targetChannelId != null,
            onClick = {
                td.sendProcessedToChannel(targetChannelId!!, processedPath!!, caption = "")
                status = "✅ נשלח לערוץ"
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("שלח לערוץ") }

        if (targetChannelId == null) Text("הגדר יעד שליחה למעלה (ערוץ ציבורי) לפני שליחה.", color = Color.Gray)
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
private fun RectPickerOverlay(
    onRectsChanged: (List<NormRect>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Rect?>(null) }
    var rects by remember { mutableStateOf<List<Rect>>(emptyList()) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { pos ->
                    start = pos
                    current = Rect(pos, pos)
                },
                onDrag = { change, _ ->
                    val s = start ?: return@detectDragGestures
                    current = Rect(s, change.position)
                },
                onDragEnd = {
                    val c = current
                    if (c != null && c.width > 12f && c.height > 12f) {
                        rects = rects + c
                        val norm = rects.map {
                            NormRect(
                                x = (it.left / size.width).coerceIn(0f, 1f),
                                y = (it.top / size.height).coerceIn(0f, 1f),
                                w = (it.width / size.width).coerceIn(0f, 1f),
                                h = (it.height / size.height).coerceIn(0f, 1f)
                            )
                        }
                        onRectsChanged(norm)
                    }
                    start = null
                    current = null
                }
            )
        }
    ) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 3f, cap = StrokeCap.Round)
            rects.forEach { r ->
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.width, r.height),
                    style = stroke
                )
            }
            current?.let { c ->
                drawRect(
                    color = Color.Yellow,
                    topLeft = Offset(c.left, c.top),
                    size = androidx.compose.ui.geometry.Size(c.width, c.height),
                    style = stroke
                )
            }
        }
    }
}
