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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioTheme { StudioScreen() } }
    }
}

private val Obsidian = Color(0xFF080807)
private val ObsidianPanel = Color(0xFF11100D)
private val Gold24 = Color(0xFFD4AF37)
private val GoldSoft = Color(0xFFE6C65C)
private val Ivory = Color(0xFFF4F0E6)
private val MutedGold = Color(0xFF9F8750)

private val StudioTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = Typography().displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = Typography().displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.Serif),
    titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.Serif),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.Serif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.Serif),
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.Serif),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.Serif),
    labelSmall = Typography().labelSmall.copy(fontFamily = FontFamily.Serif)
)

private val StudioColors = darkColorScheme(
    primary = Gold24,
    onPrimary = Obsidian,
    secondary = GoldSoft,
    onSecondary = Obsidian,
    background = Obsidian,
    onBackground = Ivory,
    surface = ObsidianPanel,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF201D16),
    onSurfaceVariant = Color(0xFFD0C8B6),
    outline = MutedGold,
    error = Color(0xFFFF8A80)
)

@Composable
private fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColors,
        typography = StudioTypography,
        content = content
    )
}

@Composable
private fun StudioLogo() {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = Obsidian,
            border = androidx.compose.foundation.BorderStroke(1.dp, Gold24)
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    "MA",
                    color = Gold24,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Column {
            Text(
                "MOJE ALTEREGO",
                color = Gold24,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "AI STUDIO · PRIVATE GENERATION",
                color = Color(0xFFB8AE9A),
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.1.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ModelLibraryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5E4D26))
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("BIBLIOTEKA MODELI", color = Gold24, style = MaterialTheme.typography.labelLarge)
            Text(
                "GGUF / SAFE-TENSORS · prywatny host ComfyUI",
                color = Ivory,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Pliki modeli pozostają na prywatnym hoście GPU. Telefon steruje katalogiem i workflow — nie pakuje wielogigabajtowych modeli do APK.",
                color = Color(0xFFC9C1B0),
                style = MaterialTheme.typography.bodySmall
            )
            ModelRow("OBRAZ", "Flux / Qwen Image / Z-Image", "models/gguf/")
            ModelRow("WAN 2.1", "T2V / I2V · GGUF", "models/gguf/ + text_encoders/wan/")
            ModelRow("WAN 2.2", "T2V / I2V · GGUF", "models/unet/ + text_encoders/")
            ModelRow("VAE / ENCODER", "WAN VAE + UMT5", "models/vae/ + models/text_encoders/")
        }
    }
}

@Composable
private fun ModelRow(kind: String, model: String, path: String) {
    Surface(
        color = Color(0xFF0C0B09),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332A18))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(kind, color = GoldSoft, style = MaterialTheme.typography.labelSmall)
                Text("GGUF", color = MutedGold, style = MaterialTheme.typography.labelSmall)
            }
            Text(model, color = Ivory, style = MaterialTheme.typography.bodyMedium)
            Text(path, color = Color(0xFF9E9583), style = MaterialTheme.typography.bodySmall)
        }
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
    var modelFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val modelDir = File(context.filesDir, "models").apply { mkdirs() }
            val names = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: return@mapNotNull null
                    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            File(modelDir, safe).outputStream().use { output -> input.copyTo(output) }
                        } ?: return@mapNotNull null
                        safe
                    }.getOrNull()
                }
            }
            modelFiles = (modelFiles + names).distinct()
            status = "Dodano " + names.size + " modeli do prywatnego katalogu /models."
        }
    }
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

    fun saveMedia(jobId: String, index: Int, filename: String?) {
        scope.launch {
            busy = true
            status = "Pobieranie wyniku do galerii…"
            try {
                val body = api().getMedia(authorization(), approvalToken.trim(), jobId, index)
                val mime = body.contentType()?.toString() ?: mimeTypeForFilename(filename)
                val safeName = (filename ?: "aistudio-$jobId-$index").replace(Regex("[^A-Za-z0-9._-]"), "_")
                val temp = File(context.cacheDir, "aistudio-save-$jobId-$index-$safeName")
                body.byteStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, temp, safeName, mime)
                    status = "Zapisano w galerii."
                } else {
                    status = "Zapisywanie do galerii wymaga Androida 10 lub nowszego."
                }
                temp.delete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Nie udało się zapisać wyniku: " + (e.localizedMessage ?: "błąd")
            } finally {
                busy = false
            }
        }
    }

    fun cancelCurrentJob() {
        val current = job ?: return
        if (current.status in setOf("COMPLETED", "FAILED", "CANCELLED", "UNKNOWN")) return
        scope.launch {
            busy = true
            status = "Anulowanie zadania…"
            try {
                val result = api().cancelJob(authorization(), approvalToken.trim(), current.id)
                if (result.cancelled) {
                    job = current.copy(status = result.status, progress = 0.0, queue_position = null)
                    status = "Zadanie anulowane."
                } else {
                    status = "Zadanie nie wymagało anulowania."
                }
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

        val socket = StudioApi.openProgressWebSocket(
            server,
            authorization(),
            approvalToken.trim(),
            currentJob.id,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "progress" -> {
                                val fraction = json.optDouble("progress", 0.0).coerceIn(0.0, 1.0)
                                scope.launch {
                                    job = job?.copy(status = "RUNNING", progress = fraction)
                                    status = "Generowanie: " + "%.1f".format(fraction * 100) + "%"
                                }
                            }
                            "executing", "execution_start", "execution_cached", "executed" -> {
                                scope.launch { status = "Generowanie w toku." }
                            }
                            "terminal" -> {
                                val terminalStatus = json.optString("status", "UNKNOWN")
                                scope.launch {
                                    job = job?.copy(status = terminalStatus, progress = if (terminalStatus == "COMPLETED") 1.0 else 0.0)
                                    status = when (terminalStatus) {
                                        "COMPLETED" -> "Generowanie zakończone."
                                        "FAILED" -> "Generowanie zakończone błędem."
                                        "CANCELLED" -> "Generowanie anulowane."
                                        else -> "Stan zadania: " + terminalStatus
                                    }
                                }
                                webSocket.close(1000, "terminal")
                            }
                            "upstream_unavailable" -> scope.launch {
                                status = "Kanał postępu chwilowo niedostępny; działa odpytywanie awaryjne."
                            }
                        }
                    } catch (_: Exception) {
                        // Polling remains the authoritative fallback.
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch {
                        if (job?.status !in setOf("COMPLETED", "FAILED", "CANCELLED", "UNKNOWN")) {
                            status = "Kanał postępu niedostępny; używam odpytywania awaryjnego."
                        }
                    }
                }
            }
        )
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            socket.close(1000, "screen-lifecycle")
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

    Scaffold(
        containerColor = Obsidian,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(com.mojealterego.aistudio.R.drawable.ic_ai_studio_logo),
                            "Moje Alterego AI Studio",
                            Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MOJE ALTEREGO", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("AI STUDIO", color = Ivory, fontFamily = FontFamily.Serif, letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Obsidian)
            )
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().background(Obsidian).padding(insets)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("PRIVATE GENERATION WORKSPACE", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("OBSIDIAN · 24K GOLD · PRIVATE GPU", color = MutedGold, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.labelSmall)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioModeCard("IMAGE", "OBRAZ", "FLUX · QWEN · SDXL", mode == "IMAGE", {
                        mode = "IMAGE"
                        workflows.firstOrNull { it.type.equals(mode, true) }?.let { next ->
                            selectedId = next.id
                            parameterValues = next.parameters.mapValues { (_, p) -> p.default?.toString().orEmpty() }
                        }
                        resetApproval()
                    }, Modifier.weight(1f))
                    StudioModeCard("VIDEO", "WIDEO", "WAN 2.1 · WAN 2.2", mode == "VIDEO", {
                        mode = "VIDEO"
                        workflows.firstOrNull { it.type.equals(mode, true) }?.let { next ->
                            selectedId = next.id
                            parameterValues = next.parameters.mapValues { (_, p) -> p.default?.toString().orEmpty() }
                        }
                        resetApproval()
                    }, Modifier.weight(1f))
                }

                DarkCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("MODEL VAULT", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, style = MaterialTheme.typography.titleMedium)
                            Text("GGUF · WAN 2.1 · WAN 2.2 · SAFETENSORS · LORA · VAE", color = Color(0xFFBEB5A2), fontFamily = FontFamily.Serif, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(modelFiles.size.toString().padStart(2, '0'), color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ModelTypeBadge("GGUF", modelFiles.count { it.lowercase().endsWith(".gguf") }, Modifier.weight(1f))
                        ModelTypeBadge("WAN", modelFiles.count { it.lowercase().contains("wan") }, Modifier.weight(1f))
                        ModelTypeBadge("ALL", modelFiles.size, Modifier.weight(1f))
                    }

                    Surface(
                        Modifier.fillMaxWidth(),
                        color = Color(0xFF0B0A08),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4B3A18))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                if (modelFiles.isEmpty()) "KATALOG GGUF / WAN JEST PUSTY" else "MODELE W KATALOGU",
                                color = Ivory,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (modelFiles.isEmpty())
                                    "Wybierz pliki GGUF, WAN, safetensors, LoRA lub VAE. Zostaną skopiowane do prywatnego katalogu aplikacji."
                                else
                                    modelFiles.takeLast(5).joinToString("  ·  "),
                                color = Color(0xFFAAA18F),
                                fontFamily = FontFamily.Serif,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                        }
                    }

                    Button(
                        onClick = { modelPicker.launch(arrayOf("application/octet-stream", "application/*", "model/*")) },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold24, contentColor = Obsidian),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("＋  DODAJ PLIK GGUF / MODEL", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                    }
                }

                DarkCard {
                    Text("GENERATOR", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it; resetApproval() },
                        label = { Text("Prompt") },
                        placeholder = { Text("Opisz obraz lub film…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    OutlinedTextField(
                        value = negative,
                        onValueChange = { negative = it; resetApproval() },
                        label = { Text("Prompt negatywny") },
                        placeholder = { Text("Opcjonalnie") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { preparePreview() },
                            enabled = !busy && selected != null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("PRZYGOTUJ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { approveAndGenerate() },
                            enabled = !busy && preview != null && approvalToken.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold24, contentColor = Obsidian),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("GENERUJ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                    }
                }

                DarkCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("WORKFLOW", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                        OutlinedButton(onClick = { loadWorkflows() }, enabled = !busy, shape = RoundedCornerShape(9.dp)) {
                            Text(if (busy) "…" else "POBIERZ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        }
                    }
                    workflows.filter { it.type.equals(mode, true) }.forEach { workflow ->
                        Surface(
                            Modifier.fillMaxWidth().clickable {
                                selectedId = workflow.id
                                parameterValues = workflow.parameters.mapValues { (_, p) -> p.default?.toString().orEmpty() }
                                resetApproval()
                            },
                            color = if (selectedId == workflow.id) Color(0xFF19150D) else Color(0xFF0B0A08),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedId == workflow.id) Gold24 else Color(0xFF332A18))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (selectedId == workflow.id) "✓" else "○", color = Gold24, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(workflow.label, color = Ivory, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                                    Text("${workflow.id} · v${workflow.version}", color = MutedGold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (selected != null) {
                    DarkCard {
                        Text("PARAMETRY", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                        selected.parameters.forEach { (name, spec) ->
                            val isPrompt = name.equals("prompt", true) || name.equals("positive_prompt", true)
                            val isNegative = name.equals("negative_prompt", true) || name.equals("negative", true)
                            if (!isPrompt && !isNegative) {
                                if (!spec.choices.isNullOrEmpty()) {
                                    var expanded by remember(name, selected.id) { mutableStateOf(false) }
                                    val current = parameterValues[name].orEmpty().ifBlank { spec.default?.toString().orEmpty() }
                                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                        OutlinedTextField(
                                            value = current,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(if (spec.required) "${name} *" else name) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(expanded, { expanded = false }) {
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
                                    val checked = parameterValues[name]?.toBooleanStrictOrNull() ?: (spec.default as? Boolean ?: false)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (spec.required) "${name} *" else name, color = Ivory, fontFamily = FontFamily.Serif)
                                        Switch(checked = checked, onCheckedChange = { parameterValues = parameterValues + (name to it.toString()); resetApproval() })
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = parameterValues[name] ?: spec.default?.toString().orEmpty(),
                                        onValueChange = { parameterValues = parameterValues + (name to it); resetApproval() },
                                        label = { Text(if (spec.required) "${name} *" else name) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                DarkCard {
                    Text("POŁĄCZENIE", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    OutlinedTextField(value = server, onValueChange = { server = it; resetApproval() }, label = { Text("Backend HTTPS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; resetApproval() }, label = { Text("Klucz API") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = approvalToken, onValueChange = { approvalToken = it; resetApproval() }, label = { Text("Token zatwierdzania") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Sekrety nie są zapisywane w APK.", color = MutedGold, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodySmall)
                }

                if (preview != null) {
                    DarkCard {
                        Text("PODGLĄD OPERACJI", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        Text("Workflow: ${preview!!.workflow_id} · v${preview!!.workflow_version}", color = Ivory)
                        Text("Typ: ${preview!!.media_type}", color = Ivory)
                        Text("Skutek: ${preview!!.consequence}", color = Color(0xFFBEB5A2))
                        Button(
                            onClick = { approveAndGenerate() },
                            enabled = !busy && approvalToken.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold24, contentColor = Obsidian),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ZATWIERDŹ I URUCHOM", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                    }
                }

                DarkCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("STATUS", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        job?.let { Text(it.status, color = GoldSoft, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                    }
                    Text(status, color = Ivory, fontFamily = FontFamily.Serif)
                    job?.takeIf { it.status == "RUNNING" }?.let { current ->
                        LinearProgressIndicator(
                            progress = { current.progress.toFloat().coerceIn(0f, 1f) },
                            Modifier.fillMaxWidth(),
                            color = Gold24,
                            trackColor = Color(0xFF2A2418)
                        )
                        Text("${"%.1f".format(current.progress * 100)}% · GENEROWANIE W TOKU", color = GoldSoft, fontFamily = FontFamily.Serif)
                    }
                    job?.let { response ->
                        if (response.status in setOf("QUEUED", "RUNNING")) {
                            OutlinedButton(onClick = { cancelCurrentJob() }, enabled = !busy, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("ANULUJ GENEROWANIE", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (response.outputs.isNotEmpty()) {
                            Text("WYNIKI", color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                            response.outputs.forEachIndexed { index, output ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { openMedia(response.id, output.media_index ?: index, output.filename) },
                                        enabled = !busy,
                                        colors = ButtonDefaults.buttonColors(containerColor = Gold24, contentColor = Obsidian),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("OTWÓRZ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                                    OutlinedButton(
                                        onClick = { saveMedia(response.id, output.media_index ?: index, output.filename) },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("ZAPISZ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                                }
                            }
                            previewBitmap?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), previewName, Modifier.fillMaxWidth().heightIn(max = 420.dp), ContentScale.Fit)
                            }
                        }
                        response.error?.let { Text("Błąd: ${it}", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }


@Composable
private fun ModelTypeBadge(type: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0C0B09),
        shape = RoundedCornerShape(9.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3D3119))
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(type, color = GoldSoft, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Text(count.toString(), color = Ivory, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StudioModeCard(
    code: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF17130B) else Color(0xFF0D0C0A))
            .border(1.dp, if (selected) Gold24 else Color(0xFF40361F), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(code, color = Gold24, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (selected) Text("●", color = Gold24, fontSize = 9.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Ivory, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MutedGold, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F351D)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) return it.getString(0)
    }
    return null
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
    return when ((filename?.substringAfterLast('.', "") ?: "").lowercase()) {
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
