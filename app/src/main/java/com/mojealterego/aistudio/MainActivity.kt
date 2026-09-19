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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { StudioScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioScreen() {
    var mode by remember { mutableStateOf("IMAGE") }
    var prompt by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var workflows by remember { mutableStateOf<List<WorkflowSummary>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var parameterValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var status by remember { mutableStateOf("Skonfiguruj HTTPS backend i klucz API.") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<JobResponse?>(null) }
    val scope = rememberCoroutineScope()
    val selected = workflows.firstOrNull { it.id == selectedId }

    fun authorization() = "Bearer ${apiKey.trim()}"
    fun api(): StudioApi = StudioApi.create(server)
    fun loadWorkflows() {
        if (server.isBlank() || apiKey.isBlank()) {
            status = "Podaj adres HTTPS backendu i klucz API."
            return
        }
        scope.launch {
            busy = true
            status = "Pobieranie katalogu workflow…"
            try {
                val response = api().listWorkflows(authorization())
                workflows = response.workflows
                val first = response.workflows.firstOrNull { it.type.equals(mode, true) }
                    ?: response.workflows.firstOrNull()
                selectedId = first?.id
                parameterValues = first?.parameters?.mapValues { (_, p) -> p.default?.toString().orEmpty() } ?: emptyMap()
                status = if (workflows.isEmpty()) "Backend nie zwrócił dostępnych workflow." else "Pobrano ${workflows.size} workflow."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = errorMessage(e)
            } finally { busy = false }
        }
    }

    fun submitJob() {
        val workflow = selected
        if (workflow == null) { status = "Najpierw pobierz i wybierz workflow."; return }
        if (prompt.isBlank()) { status = "Wpisz prompt."; return }
        val values = mutableMapOf<String, Any?>()
        for ((name, spec) in workflow.parameters) {
            var raw = parameterValues[name].orEmpty()
            if (name.equals("prompt", true) || name.equals("positive_prompt", true)) raw = prompt
            if (name.equals("negative_prompt", true) || name.equals("negative", true)) raw = negative
            if (raw.isBlank() && spec.default != null) raw = spec.default.toString()
            if (raw.isBlank() && spec.required) { status = "Brakuje wymaganego parametru: $name"; return }
            if (raw.isBlank()) continue
            if (spec.min_length != null && raw.length < spec.min_length) { status = "$name: tekst jest za krótki."; return }
            if (spec.max_length != null && raw.length > spec.max_length) { status = "$name: tekst jest za długi."; return }
            if (spec.choices != null && raw !in spec.choices) { status = "$name: wybierz jedną z dozwolonych wartości."; return }
            val value: Any? = when (spec.type.lowercase()) {
                "integer", "int" -> raw.toLongOrNull() ?: run { status = "$name: oczekiwana liczba całkowita."; return }
                "number", "float", "double" -> raw.toDoubleOrNull() ?: run { status = "$name: oczekiwana liczba."; return }
                "boolean", "bool" -> raw.toBooleanStrictOrNull() ?: run { status = "$name: oczekiwana wartość true/false."; return }
                else -> raw
            }
            val numeric = (value as? Number)?.toDouble()
            if (numeric != null && spec.minimum != null && numeric < spec.minimum) { status = "$name: wartość poniżej minimum."; return }
            if (numeric != null && spec.maximum != null && numeric > spec.maximum) { status = "$name: wartość powyżej maksimum."; return }
            values[name] = value
        }
        scope.launch {
            busy = true
            status = "Wysyłanie zadania…"
            job = null
            try {
                val response = api().createJobV2(authorization(), CreateJobV2Request(workflow.id, values))
                job = response
                status = "Zadanie przyjęte: ${response.id} (${response.status})."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { status = errorMessage(e) }
            finally { busy = false }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("AI STUDIO") }) }) { insets ->
        Column(
            modifier = Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("PRIVATE GENERATION WORKSPACE", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("IMAGE" to "Obraz", "VIDEO" to "Wideo").forEachIndexed { index, pair ->
                    SegmentedButton(selected = mode == pair.first, onClick = {
                        mode = pair.first
                        val next = workflows.firstOrNull { it.type.equals(mode, true) }
                        if (next != null) {
                            selectedId = next.id
                            parameterValues = next.parameters.mapValues { (_, p) -> p.default?.toString().orEmpty() }
                        }
                    }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(pair.second) }
                }
            }
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Prompt") },
                placeholder = { Text("Opisz scenę, styl i oświetlenie…") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(value = negative, onValueChange = { negative = it }, label = { Text("Prompt negatywny (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Adres backendu HTTPS") },
                placeholder = { Text("https://twoj-serwer.example") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Klucz API") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { loadWorkflows() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Przetwarzanie…" else "Pobierz workflow")
            }
            if (workflows.isNotEmpty()) {
                Text("Workflow", style = MaterialTheme.typography.titleMedium)
                workflows.filter { it.type.equals(mode, true) }.forEach { workflow ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = selectedId == workflow.id, onClick = {
                            selectedId = workflow.id
                            parameterValues = workflow.parameters.mapValues { (_, p) -> p.default?.toString().orEmpty() }
                            job = null
                        })
                        Column(Modifier.weight(1f)) {
                            Text(workflow.label, style = MaterialTheme.typography.bodyLarge)
                            Text("${workflow.id} · v${workflow.version}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            selected?.let { workflow ->
                Text("Parametry workflow", style = MaterialTheme.typography.titleMedium)
                workflow.parameters.forEach { (name, spec) ->
                    val isPrompt = name.equals("prompt", true) || name.equals("positive_prompt", true)
                    val isNegative = name.equals("negative_prompt", true) || name.equals("negative", true)
                    if (!isPrompt && !isNegative) {
                        if (!spec.choices.isNullOrEmpty()) {
                            var expanded by remember(name, workflow.id) { mutableStateOf(false) }
                            val current = parameterValues[name].orEmpty().ifBlank { spec.default?.toString().orEmpty() }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                OutlinedTextField(value = current, onValueChange = {}, readOnly = true,
                                    label = { Text(if (spec.required) "$name *" else name) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth())
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    spec.choices.forEach { choice ->
                                        DropdownMenuItem(text = { Text(choice) }, onClick = {
                                            parameterValues = parameterValues + (name to choice); expanded = false
                                        })
                                    }
                                }
                            }
                        } else if (spec.type.equals("boolean", true) || spec.type.equals("bool", true)) {
                            val checked = parameterValues[name]?.toBooleanStrictOrNull() ?: (spec.default as? Boolean ?: false)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (spec.required) "$name *" else name)
                                Switch(checked = checked, onCheckedChange = { parameterValues = parameterValues + (name to it.toString()) })
                            }
                        } else {
                            OutlinedTextField(value = parameterValues[name] ?: spec.default?.toString().orEmpty(),
                                onValueChange = { parameterValues = parameterValues + (name to it) },
                                label = { Text(if (spec.required) "$name *" else name) },
                                supportingText = { Text(listOfNotNull(spec.minimum?.let { "min $it" }, spec.maximum?.let { "max $it" }, spec.max_length?.let { "max $it znaków" }).joinToString(" · ")) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                    }
                }
            }
            Button(onClick = { submitJob() }, enabled = !busy && selected != null, modifier = Modifier.fillMaxWidth()) {
                Text("Wyślij zadanie ${if (mode == "IMAGE") "obrazu" else "wideo"}")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            job?.let { response ->
                HorizontalDivider()
                Text("Zadanie: ${response.id}", style = MaterialTheme.typography.titleSmall)
                Text("Status: ${response.status} · postęp: ${response.progress}%")
                response.error?.let { Text("Błąd: $it", color = MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider()
            Text("Klucz API pozostaje wyłącznie w pamięci ekranu; nie jest zapisywany. Odświeżenie aplikacji wymaga ponownego wpisania.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun errorMessage(error: Exception): String = when (error) {
    is HttpException -> when (error.code()) {
        401 -> "Odmowa dostępu: sprawdź klucz API."
        503 -> "Backend nie ma skonfigurowanego klucza API."
        else -> "Błąd HTTP ${error.code()}: ${error.message()}"
    }
    is IOException -> "Brak połączenia z backendem: ${error.localizedMessage ?: "błąd sieci"}"
    else -> error.localizedMessage ?: "Nieoczekiwany błąd."
}
