package com.mojealterego.aistudio

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID

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
    var approvalToken by remember { mutableStateOf("") }
    var workflows by remember { mutableStateOf<List<WorkflowSummary>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var parameterValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var status by remember { mutableStateOf("Skonfiguruj HTTPS backend, klucz API i osobny token zatwierdzania.") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<JobResponse?>(null) }
    var preview by remember { mutableStateOf<ApprovalPreviewResponse?>(null) }
    var sessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var taskId by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selected = workflows.firstOrNull { it.id == selectedId }

    fun authorization() = "Bearer ${apiKey.trim()}"
    fun api(): StudioApi = StudioApi.create(server)

    fun resetApproval() {
        preview = null
        taskId = null
    }

    fun openMedia(jobId: String, index: Int, filename: String?) {
        scope.launch {
            busy = true
            status = "Pobieranie wyniku…"
            try {
                val body = api().getMedia(authorization(), approvalToken.trim(), jobId, index)
                val mime = body.contentType()?.toString() ?: mimeTypeForFilename(filename)
                val safeName = (filename ?: "output-$index.bin").replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(context.cacheDir, "aistudio-$jobId-$index-$safeName")
                body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                if (mime.startsWith("image/")) {
                    previewBitmap = withContext(Dispatchers.IO) { decodePreviewBitmap(file) }
                    previewName = safeName
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Otwórz wynik"))
                status = "Wynik pobrany przez prywatny proxy backendu."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = errorMessage(e)
            } finally {
                busy = false
            }
        }
    }

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
                resetApproval()
                status = if (workflows.isEmpty()) "Backend nie zwrócił dostępnych workflow." else "Pobrano ${workflows.size} workflow."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = errorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun collectParameters(workflow: WorkflowSummary): Map<String, Any?>? {
        val values = mutableMapOf<String, Any?>()
        for ((name, spec) in workflow.parameters) {
            var raw = parameterValues[name].orEmpty()
            if (name.equals("prompt", true) || name.equals("positive_prompt", true)) raw = prompt
            if (name.equals("negative_prompt", true) || name.equals("negative", true)) raw = negative
            if (raw.isBlank() && spec.default != null) raw = spec.default.toString()
            if (raw.isBlank() && spec.required) {
                status = "Brakuje wymaganego parametru: ${name}"
                return null
            }
            if (raw.isBlank()) continue
            if (spec.min_length != null && raw.length < spec.min_length) {
                status = "${name}: tekst jest za krótki."
                return null
            }
            if (spec.max_length != null && raw.length > spec.max_length) {
                status = "${name}: tekst jest za długi."
                return null
            }
            if (spec.choices != null && raw !in spec.choices) {
                status = "${name}: wybierz jedną z dozwolonych wartości."
                return null
            }
            val value: Any? = when (spec.type.lowercase()) {
                "integer", "int" -> raw.toLongOrNull() ?: run {
                    status = "${name}: oczekiwana liczba całkowita."
                    return null
                }
                "number", "float", "double" -> raw.toDoubleOrNull() ?: run {
                    status = "${name}: oczekiwana liczba."
                    return null
                }
                "boolean", "bool" -> raw.toBooleanStrictOrNull() ?: run {
                    status = "${name}: oczekiwana wartość true/false."
                    return null
                }
                else -> raw
            }
            val numeric = (value as? Number)?.toDouble()
            if (numeric != null && spec.minimum != null && numeric < spec.minimum) {
                status = "${name}: wartość poniżej minimum."
                return null
            }
            if (numeric != null && spec.maximum != null && numeric > spec.maximum) {
                status = "${name}: wartość powyżej maksimum."
                return null
            }
            values[name] = value
        }
        return values
    }

    fun preparePreview() {
        val workflow = selected ?: run {
            status = "Najpierw pobierz i wybierz workflow."
            return
        }
        if (prompt.isBlank()) {
            status = "Wpisz prompt."
            return
        }
        if (approvalToken.isBlank()) {
            status = "Podaj osobny token zatwierdzania."
            return
        }
        val values = collectParameters(workflow) ?: return
        val newTaskId = UUID.randomUUID().toString()
        scope.launch {
            busy = true
            status = "Tworzenie niezmiennego podglądu operacji…"
            try {
                val response = api().approvalPreview(
                    authorization(),
                    PreviewRequest(workflow.id, values, sessionId, newTaskId)
                )
                preview = response
                taskId = newTaskId
                job = null
                status = "Podgląd gotowy. Generowanie nie zostało jeszcze uruchomione."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = errorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun approveAndGenerate() {
        val workflow = selected
        val currentPreview = preview
        val currentTaskId = taskId
        if (workflow == null || currentPreview == null || currentTaskId == null) {
            status = "Brak ważnego podglądu zgody."
            return
        }
        scope.launch {
            busy = true
            status = "Weryfikacja zgody i utworzenie jednorazowego uprawnienia…"
            try {
                val approval = api().createApproval(
                    authorization(),
                    approvalToken.trim(),
                    ApprovalRequest(
                        workflow_id = workflow.id,
                        parameters = currentPreview.parameters,
                        session_id = sessionId,
                        task_id = currentTaskId,
                        preview_digest = currentPreview.preview_digest
                    )
                )
                status = "Zgoda przyjęta. Uruchamianie workflow na prywatnym backendzie…"
                val response = api().createJobV2(
                    authorization(),
                    approvalToken.trim(),
                    CreateJobV2Request(
                        workflow_id = workflow.id,
                        parameters = currentPreview.parameters,
                        grant_id = approval.grant_id,
                        session_id = sessionId,
                        task_id = currentTaskId,
                        client_id = UUID.randomUUID().toString()
                    )
                )
                job = response
                preview = null
                taskId = null
                status = "Zadanie przyjęte: ${response.id} (${response.status})."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = errorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("aistudio-") && System.currentTimeMillis() - it.lastModified() > 24L * 60L * 60L * 1000L }
                ?.forEach { it.delete() }
        }
    }

    LaunchedEffect(job?.id, server, apiKey, approvalToken) {
        val currentJob = job ?: return@LaunchedEffect
        if (server.isBlank() || apiKey.isBlank() || approvalToken.isBlank()) return@LaunchedEffect
        if (currentJob.status in setOf("COMPLETED", "FAILED", "CANCELLED", "UNKNOWN")) return@LaunchedEffect

        while (true) {
            delay(2500)
            try {
                val refreshed = api().getJob(authorization(), approvalToken.trim(), currentJob.id)
                job = refreshed
                status = when (refreshed.status) {
                    "COMPLETED" -> "Generowanie zakończone."
                    "FAILED" -> "Backend zgłosił błąd generowania."
                    "CANCELLED" -> "Generowanie anulowane."
                    "UNKNOWN" -> "Stan zadania jest niepewny; nie wysyłaj ponownie tego samego jednorazowego zatwierdzenia."
                    "RUNNING" -> "Generowanie w toku."
                    "QUEUED" -> "W kolejce: " + (refreshed.queue_position?.toString() ?: "oczekuje") + "."
                    else -> "Stan zadania: " + refreshed.status + "."
                }
                if (refreshed.status in setOf("COMPLETED", "FAILED", "UNKNOWN")) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = errorMessage(e)
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("AI STUDIO") }) }) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("PRIVATE GENERATION WORKSPACE", style = MaterialTheme.typography.labelMedium)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("IMAGE" to "Obraz", "VIDEO" to "Wideo").forEachIndexed { index, pair ->
                    SegmentedButton(
                        selected = mode == pair.first,
                        onClick = {
                            mode = pair.first
                            val next = workflows.firstOrNull { it.type.equals(mode, true) }
                            if (next != null) {
                                selectedId = next.id
                                parameterValues = next.parameters.mapValues { (_, p) -> p.default?.toString().orEmpty() }
                                resetApproval()
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(pair.second) }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it; resetApproval() },
                label = { Text("Prompt") },
                placeholder = { Text("Opisz scenę, styl i oświetlenie…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = negative,
                onValueChange = { negative = it; resetApproval() },
                label = { Text("Prompt negatywny (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = server,
                onValueChange = { server = it; resetApproval() },
                label = { Text("Adres backendu HTTPS") },
                placeholder = { Text("https://twoj-serwer.example") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; resetApproval() },
                label = { Text("Klucz API") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = approvalToken,
                onValueChange = { approvalToken = it; resetApproval() },
                label = { Text("Osobny token zatwierdzania") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Nie jest tym samym sekretem co klucz API i nie jest zapisywany.") }
            )

            OutlinedButton(
                onClick = { loadWorkflows() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                            resetApproval()
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
                                OutlinedTextField(
                                    value = current,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (spec.required) "${name} *" else name) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    spec.choices.forEach { choice ->
                                        DropdownMenuItem(text = { Text(choice) }, onClick = {
                                            parameterValues = parameterValues + (name to choice)
                                            expanded = false
                                            resetApproval()
                                        })
                                    }
                                }
                            }
                        } else if (spec.type.equals("boolean", true) || spec.type.equals("bool", true)) {
                            val checked = parameterValues[name]?.toBooleanStrictOrNull()
                                ?: (spec.default as? Boolean ?: false)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (spec.required) "${name} *" else name)
                                Switch(checked = checked, onCheckedChange = {
                                    parameterValues = parameterValues + (name to it.toString())
                                    resetApproval()
                                })
                            }
                        } else {
                            OutlinedTextField(
                                value = parameterValues[name] ?: spec.default?.toString().orEmpty(),
                                onValueChange = {
                                    parameterValues = parameterValues + (name to it)
                                    resetApproval()
                                },
                                label = { Text(if (spec.required) "${name} *" else name) },
                                supportingText = {
                                    Text(
                                        listOfNotNull(
                                            spec.minimum?.let { "min ${it}" },
                                            spec.maximum?.let { "max ${it}" },
                                            spec.max_length?.let { "max ${it} znaków" }
                                        ).joinToString(" · ")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            if (preview == null) {
                Button(
                    onClick = { preparePreview() },
                    enabled = !busy && selected != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. Przygotuj podgląd i zakres zgody")
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("PODGLĄD OPERACJI", style = MaterialTheme.typography.titleMedium)
                        Text("Workflow: ${preview!!.workflow_id} · v${preview!!.workflow_version}")
                        Text("Typ: ${preview!!.media_type}")
                        Text("Działanie: ${preview!!.action}")
                        Text("Zakres: ${preview!!.resource}")
                        Text("Skutek: ${preview!!.consequence}")
                        Text("Zadanie: ${preview!!.task_id}")
                        Text("Uprawnienie: jednorazowe, ważne do 5 minut")
                        Text("Digest: ${preview!!.preview_digest}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Text(
                            "Sprawdź parametry powyżej. Zatwierdzenie uruchomi rzeczywiste zadanie na backendzie.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { approveAndGenerate() },
                            enabled = !busy && approvalToken.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("2. ZATWIERDŹ I URUCHOM GENEROWANIE")
                        }
                        OutlinedButton(
                            onClick = { resetApproval(); status = "Podgląd unieważniony." },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Anuluj podgląd")
                        }
                    }
                }
            }

            Text(status, style = MaterialTheme.typography.bodyMedium)

            job?.let { response ->
                HorizontalDivider()
                Text("Zadanie: ${response.id}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (response.status) {
                        "QUEUED" -> "Status: W kolejce" + (response.queue_position?.let { " · pozycja " + it } ?: "")
                        "RUNNING" -> "Status: Uruchomione · generowanie w toku"
                        "COMPLETED" -> "Status: Zakończone"
                        "CANCELLED" -> "Status: Anulowane"
                        "FAILED" -> "Status: Błąd"
                        "UNKNOWN" -> "Status: Niepewny"
                        else -> "Status: " + response.status
                    }
                )
                if (response.status in setOf("QUEUED", "RUNNING")) {
                    OutlinedButton(
                        onClick = { cancelCurrentJob() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Anuluj generowanie") }
                }
                if (response.outputs.isNotEmpty()) {
                    Text("Wyniki", style = MaterialTheme.typography.titleMedium)
                    response.outputs.forEachIndexed { index, output ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                listOfNotNull(output.filename, output.subfolder, output.type, output.format).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { openMedia(response.id, output.media_index ?: index, output.filename) },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Otwórz") }
                                OutlinedButton(
                                    onClick = { saveMedia(response.id, output.media_index ?: index, output.filename) },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Zapisz") }
                            }
                        }
                    }
                    previewBitmap?.let { bitmap ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Podgląd obrazu" + (previewName?.let { ": " + it } ?: ""))
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = previewName,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                    Text(
                        "Media są pobierane przez uwierzytelniony backend; ComfyUI pozostaje prywatne i nie jest dostępne z telefonu.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                response.error?.let {
                    Text("Błąd: ${it}", color = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider()
            Text(
                "Klucz API i token zatwierdzania pozostają wyłącznie w pamięci ekranu; nie są zapisywane w aplikacji.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun errorMessage(error: Exception): String = when (error) {
    is HttpException -> when (error.code()) {
        401 -> "Odmowa dostępu: sprawdź klucz API."
        403 -> "Odmowa: wymagany jest prawidłowy osobny token zatwierdzania."
        409 -> "Podgląd zgody nie pasuje do aktualnych parametrów. Utwórz nowy podgląd."
        410 -> "Stary, arbitralny endpoint generowania jest wyłączony."
        502 -> "Backend nie potwierdził prawidłowego wyniku ComfyUI. Nie ponawiaj tego samego jednorazowego zatwierdzenia."
        503 -> "Backend nie jest gotowy lub nie ma skonfigurowanego klucza API."
        else -> "Błąd HTTP ${error.code()}: ${error.message()}"
    }
    is IOException -> "Brak połączenia z backendem: " + (error.localizedMessage ?: "błąd sieci")
    else -> error.localizedMessage ?: "Nieoczekiwany błąd."
}


private fun mimeTypeForFilename(filename: String?): String {
    return when (filename?.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}

private fun decodePreviewBitmap(file: File, maxDimension: Int = 1600): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )
}

private fun saveToMediaStore(
    context: android.content.Context,
    file: File,
    displayName: String,
    mimeType: String
) {
    val resolver = context.contentResolver
    val isVideo = mimeType.startsWith("video/")
    val collection = if (isVideo) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (isVideo) "Movies/AI Studio" else "Pictures/AI Studio"
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri: Uri = resolver.insert(collection, values)
        ?: error("MediaStore nie utworzył wpisu")
    try {
        resolver.openOutputStream(uri, "w")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Nie można otworzyć docelowego pliku")
        val publish = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, publish, null, null)
    } catch (error: Exception) {
        resolver.delete(uri, null, null)
        throw error
    }
}
