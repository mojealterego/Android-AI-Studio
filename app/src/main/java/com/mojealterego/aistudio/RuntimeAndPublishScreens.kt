package com.mojealterego.aistudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RuntimeSlot(
    val id: String,
    val title: String,
    val purpose: String,
    val path: String? = null,
    val state: String = "EMPTY"
)

@Composable
fun ModelRuntimeScreen(onBack: () -> Unit) {
    var slots by remember {
        mutableStateOf(
            listOf(
                RuntimeSlot("CHAT", "CHAT / JARVIS", "rozmowa + agent"),
                RuntimeSlot("CODE", "CODING", "kod + repozytoria"),
                RuntimeSlot("VISION", "IMAGE / VIDEO", "vision + generator routing")
            )
        )
    }
    var active by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && active != null) {
            slots = slots.map {
                if (it.id == active) it.copy(path = uri.toString(), state = "READY") else it
            }
            active = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070706)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← HUB") }
            Text("MODEL VAULT", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "TRZY RÓWNOLEGŁE SLOTY GGUF",
            color = Color(0xFFF4F0E6),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Jedna aplikacja, trzy role modeli. Każdy slot ma własny stan załadowania i może pozostać aktywny równocześnie.",
            color = Color(0xFFA89B7A),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(slots.size) { index ->
                val slot = slots[index]
                Surface(
                    color = Color(0xFF12110E),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (slot.state == "READY") Color(0xFFD4AF37) else Color(0xFF3B311B)
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(slot.title, color = Color(0xFFF4F0E6), fontWeight = FontWeight.Bold)
                                Text(slot.purpose, color = Color(0xFFA89B7A), fontSize = 11.sp)
                            }
                            Text(
                                slot.state,
                                color = if (slot.state == "READY") Color(0xFFF0D77A) else Color(0xFFA89B7A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            slot.path ?: "Brak modelu GGUF",
                            color = Color(0xFFC5BCA9),
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(9.dp))
                        Button(
                            onClick = {
                                active = slot.id
                                picker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD4AF37),
                                contentColor = Color(0xFF070706)
                            )
                        ) {
                            Text(if (slot.state == "READY") "ZMIEŃ GGUF" else "ZAŁADUJ GGUF")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "ARCHITEKTURA: CHAT i CODING korzystają z własnych kontekstów; IMAGE/VIDEO otrzymuje dostęp do vision/generator routera. Fizyczne jednoczesne załadowanie zależy od RAM/VRAM i wybranego backendu (llama.cpp/kompatybilny runtime).",
            color = Color(0xFF8F866F),
            fontSize = 10.sp
        )
    }
}

@Composable
fun SocialPublishScreen(onBack: () -> Unit) {
    var fileName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("READY") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) fileName = uri.toString()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070706)).padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← HUB") }
        Text("PUBLISH / MEDIA", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("PUBLIKOWANIE WYGENEROWANYCH PLIKÓW", color = Color(0xFFF4F0E6), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Jeden artefakt → wybór kanałów → publikacja przez bezpieczne konektory.", color = Color(0xFFA89B7A), fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
            Text("WYBIERZ IMAGE / VIDEO")
        }
        Spacer(Modifier.height(10.dp))
        Text(fileName ?: "Nie wybrano pliku", color = Color(0xFFC5BCA9), fontSize = 11.sp)
        Spacer(Modifier.height(18.dp))
        listOf("INSTAGRAM", "FACEBOOK", "YOUTUBE", "TIKTOK", "X", "LINKEDIN").forEach { channel ->
            Surface(
                color = Color(0xFF12110E),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF3B311B)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = {})
                    Text(channel, color = Color(0xFFF4F0E6), fontWeight = FontWeight.Bold)
                }
            }
        }
        Button(
            onClick = { status = "QUEUED" },
            enabled = fileName != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37), contentColor = Color(0xFF070706))
        ) {
            Text("PRZYGOTUJ PUBLIKACJĘ")
        }
        Text("STATUS: $status", color = Color(0xFFA89B7A), fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
    }
}
