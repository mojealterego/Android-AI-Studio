package com.mojealterego.aistudio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val OmniBg = Color(0xFF070706)
private val OmniPanel = Color(0xFF12110E)
private val OmniGold = Color(0xFFD4AF37)
private val OmniText = Color(0xFFF4F0E6)
private val OmniMuted = Color(0xFFA89B7A)

@Composable
fun ModelRuntimeScreen(onBack: () -> Unit) {
    var slots by remember {
        mutableStateOf(
            listOf(
                LoadedModel(ModelSlot.CHAT_CODE, "chat-code-model.gguf", "/models/chat-code/chat-code-model.gguf"),
                LoadedModel(ModelSlot.IMAGE, "image-model.gguf", "/models/image/image-model.gguf"),
                LoadedModel(ModelSlot.VIDEO, "video-model.gguf", "/models/video/video-model.gguf")
            )
        )
    }
    Column(Modifier.fillMaxSize().background(OmniBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← WRÓĆ", color = OmniGold) }
        Text("MODEL RUNTIME", color = OmniGold, style = MaterialTheme.typography.headlineSmall)
        Text("Dokładnie trzy rezydentne sloty GGUF: 1) CHAT + CODE, 2) IMAGE, 3) VIDEO. Każdy slot może pozostać załadowany równocześnie.", color = OmniText)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(slots) { model ->
                Surface(color = OmniPanel, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, if (model.loaded) OmniGold else Color(0xFF3B311B))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.slot.title, color = OmniGold, fontWeight = FontWeight.Bold)
                            Text(model.fileName, color = OmniText)
                            Text(model.path, color = OmniMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = model.loaded, onCheckedChange = { enabled ->
                            slots = slots.map { if (it.slot == model.slot) it.copy(loaded = enabled) else it }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun SocialPublishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Uri?>(null) }
    val targets = listOf(
        SocialTarget("tiktok", "TikTok", true, true, "Direct Post / Upload"),
        SocialTarget("youtube", "YouTube", false, true, "OAuth Upload"),
        SocialTarget("instagram", "Instagram", true, true, "Share / API adapter"),
        SocialTarget("facebook", "Facebook", true, true, "Share / API adapter")
    )
    Column(Modifier.fillMaxSize().background(OmniBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← WRÓĆ", color = OmniGold) }
        Text("PUBLISH", color = OmniGold, style = MaterialTheme.typography.headlineSmall)
        Text("Jeden eksport → wiele kanałów. Tokeny OAuth nie są przekazywane do agenta.", color = OmniText)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = caption, onValueChange = { caption = it }, modifier = Modifier.fillMaxWidth(), label = { Text("OPIS / CAPTION") })
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            context.startActivityForResult(intent, 4001)
        }) { Text("WYBIERZ IMAGE / VIDEO") }
        Spacer(Modifier.height(12.dp))
        targets.forEach { target ->
            Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = OmniPanel, border = BorderStroke(1.dp, Color(0xFF3B311B))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(target.title, color = OmniText, fontWeight = FontWeight.Bold)
                        Text(target.mode, color = OmniMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = if (target.supportsVideo) "video/*" else "image/*"
                            selected?.let { putExtra(Intent.EXTRA_STREAM, it) }
                            putExtra(Intent.EXTRA_TEXT, caption)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Udostępnij przez " + target.title))
                    }) { Text("UDOSTĘPNIJ") }
                }
            }
        }
    }
}
