package com.azreee.tglive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit
) {
    var apiIdText by remember { mutableStateOf(if (settings.apiId == 0) "" else settings.apiId.toString()) }
    var apiHash by remember { mutableStateOf(settings.apiHash) }
    var channel by remember { mutableStateOf(settings.channel) }
    var libre by remember { mutableStateOf(settings.libreUrl) }

    val canSave = apiIdText.toIntOrNull() != null && apiHash.isNotBlank()

    Scaffold(
        topBar = { TopAppBar(title = { Text("הגדרות") }) }
    ) { p ->
        Column(
            Modifier.padding(p).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("הכנס API_ID ו-API_HASH של Telegram (בחינם ב-my.telegram.org)")

            OutlinedTextField(
                value = apiIdText,
                onValueChange = { apiIdText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Telegram API ID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiHash,
                onValueChange = { apiHash = it.trim() },
                label = { Text("Telegram API HASH") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = channel,
                onValueChange = { channel = it },
                label = { Text("ערוץ יעד לשליחה (@... ציבורי)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = libre,
                onValueChange = { libre = it },
                label = { Text("שרת תרגום (LibreTranslate)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val id = apiIdText.toIntOrNull() ?: 0
                    onSave(AppSettings(id, apiHash, channel, libre))
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("שמור והפעל") }

            Text(
                "טיפ: אם שירות התרגום איטי, אפשר להחליף URL או להשאיר - ההודעות יוצגו ללא תרגום.",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
