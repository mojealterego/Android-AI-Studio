package com.mojealterego.aistudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { StudioScreen() } }
    }
}

@Composable
private fun StudioScreen() {
    var mode by remember { mutableStateOf("Obraz") }
    var prompt by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Serwer nie skonfigurowany") }

    Scaffold(topBar = { TopAppBar(title = { Text("AI STUDIO") }) }) { insets ->
        Column(
            modifier = Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("PRIVATE GENERATION WORKSPACE", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Obraz", "Wideo").forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = { mode = item },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(item) }
                }
            }
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it },
                label = { Text("Prompt") }, placeholder = { Text("Opisz scenę, styl i oświetlenie…") },
                modifier = Modifier.fillMaxWidth(), minLines = 4
            )
            OutlinedTextField(
                value = negative, onValueChange = { negative = it },
                label = { Text("Prompt negatywny (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Adres backendu HTTPS") },
                placeholder = { Text("https://twoj-serwer.example") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { status = if (prompt.isBlank()) "Wpisz prompt." else "Backend API nie jest jeszcze podłączone." },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generuj $mode") }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("Historia i galeria pojawią się po podłączeniu backendu.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
