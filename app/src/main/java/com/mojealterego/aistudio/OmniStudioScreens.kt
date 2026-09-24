package com.mojealterego.aistudio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val OmniBg = Color(0xFF070706)
private val OmniPanel = Color(0xFF12110E)
private val OmniGold = Color(0xFFD4AF37)
private val OmniText = Color(0xFFF4F0E6)
private val OmniMuted = Color(0xFFA89B7A)

private data class SlotUi(val id: String, val title: String, val purpose: String)

private val slotsUi = listOf(
    SlotUi("chat-code", "CHAT + CODE", "GGUF rezydentny dla chatu/JARVIS i kodowania."),
    SlotUi("image", "IMAGE", "GGUF/runtime dla generowania obrazu."),
    SlotUi("video", "VIDEO", "GGUF/runtime dla generowania wideo.")
)

@Composable
fun ModelRuntimeScreen(onBack: () -> Unit) {
    var backend by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("GGUF") }
    var states by remember { mutableStateOf<List<RuntimeSlotState>>(emptyList()) }
    var models by remember { mutableStateOf<List<HfModel>>(emptyList()) }
    var message by remember { mutableStateOf("Wczytaj stan runtime albo wyszukaj model na Hugging Face.") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(OmniBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← WRÓĆ", color = OmniGold) }
        Text("MODEL VAULT", color = OmniGold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("3 REZYDENTNE SLOTY GGUF", color = OmniText, fontWeight = FontWeight.Bold)
        Text("CHAT+CODE + IMAGE + VIDEO mogą być utrzymywane równocześnie. Stan 'loaded' pochodzi z backendowego runtime, nie z samego UI.", color = OmniMuted, fontSize = 11.sp)

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(backend, { backend = it }, label = { Text("Backend HTTPS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("Klucz API") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(enabled = backend.isNotBlank() && apiKey.isNotBlank() && !loading, onClick = {
            scope.launch {
                loading = true
                runCatching { StudioApi.create(backend).runtimeState("Bearer " + apiKey.trim()) }
                    .onSuccess { states = it.slots; message = "Runtime: " + it.simultaneous_resident_slots + " sloty rezydentne." }
                    .onFailure { message = "Runtime: " + (it.message ?: "błąd") }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("ODŚWIEŻ RUNTIME") }

        slotsUi.forEach { slot ->
            val state = states.firstOrNull { it.slot_id == slot.id }
            Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = OmniPanel, shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(slot.title, color = OmniGold, fontWeight = FontWeight.Bold)
                        Text(if (state?.loaded == true) "LOADED" else "EMPTY", color = if (state?.loaded == true) Color(0xFFF0D77A) else OmniMuted, fontSize = 10.sp)
                    }
                    Text(slot.purpose, color = OmniText, fontSize = 11.sp)
                    state?.model_name?.let { Text(it, color = OmniMuted, fontSize = 11.sp) }
                    state?.model_path?.let { Text(it, color = OmniMuted, fontSize = 9.sp) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("HUGGING FACE MODEL SEARCH", color = OmniGold, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, label = { Text("GGUF / model") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(enabled = backend.isNotBlank() && apiKey.isNotBlank() && !loading, onClick = {
                scope.launch {
                    loading = true
                    runCatching { StudioApi.create(backend).searchHuggingFace("Bearer " + apiKey.trim(), query) }
                        .onSuccess { models = it.models; message = "Hugging Face: " + it.models.size + " wyników." }
                        .onFailure { message = "Hugging Face: " + (it.message ?: "błąd") }
                    loading = false
                }
            }) { Text("SZUKAJ") }
        }
        Text(message, color = OmniMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 7.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(models) { model ->
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF0D0C0A), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(model.id ?: "unknown", color = OmniText, fontWeight = FontWeight.Bold)
                        Text("downloads " + (model.downloads ?: 0) + " · likes " + (model.likes ?: 0), color = OmniMuted, fontSize = 9.sp)
                        Text(model.tags.take(5).joinToString(" · "), color = OmniMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SocialPublishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Wybierz wygenerowany obraz albo video.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selected = uri
        status = if (uri == null) "Nie wybrano pliku." else "Plik gotowy."
    }

    Column(Modifier.fillMaxSize().background(OmniBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← WRÓĆ", color = OmniGold) }
        Text("SOCIAL PUBLISH", color = OmniGold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Jeden wygenerowany asset może zostać przekazany do aplikacji społecznościowych przez systemowy share sheet.", color = OmniText, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(caption, { caption = it }, label = { Text("CAPTION") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { picker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) { Text("WYBIERZ ASSET") }
        Text(status, color = OmniMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        Button(enabled = selected != null, onClick = { selected?.let { shareMedia(context, it, caption) } },
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OmniGold, contentColor = OmniBg)) {
            Text("PUBLIKUJ / UDOSTĘPNIJ", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text("Obsługiwane przez system: Instagram, Facebook, TikTok, YouTube i inne aplikacje zainstalowane na urządzeniu. Bezpośrednie API publikacji wymaga osobnych OAuth adapterów.", color = OmniMuted, fontSize = 10.sp)
    }
}

private fun shareMedia(context: Context, uri: Uri, caption: String) {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Opublikuj / udostępnij"))
}
