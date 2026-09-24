package com.mojealterego.aistudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AiHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiHubTheme { AiHubScreen(onOpenStudio = { startActivity(Intent(this, MainActivity::class.java)) }) } }
    }
}

private val HubBg = Color(0xFF070706)
private val HubPanel = Color(0xFF12110E)
private val HubGold = Color(0xFFD4AF37)
private val HubGold2 = Color(0xFFF0D77A)
private val HubIvory = Color(0xFFF4F0E6)
private val HubMuted = Color(0xFFA89B7A)

private data class HubModule(val group:String,val code:String,val title:String,val detail:String,val runtime:String)

private val modules = listOf(
 HubModule("CREATE","IMG","IMAGE LAB","SD · Flux · Qwen · LoRA · ControlNet · inpaint","LOCAL / GPU"),
 HubModule("CREATE","VID","VIDEO LAB","Wan · LTX · Hunyuan · I2V · keyframes","GPU WORKER"),
 HubModule("CREATE","AUD","AUDIO LAB","Bark · TTS · voice · sound design","LOCAL / GPU"),
 HubModule("CREATE","MUS","MUSIC LAB","OpenMusic · MusicGen · MIDI Transformer","GPU WORKER"),
 HubModule("CREATE","AVA","AVATAR LAB","Duix · HunyuanPortrait · lip-sync","GPU WORKER"),
 HubModule("CREATE","NOV","STORY LAB","novel · screenplay · characters · continuity","AGENT"),
 HubModule("AGENT","JAR","JARVIS","memory · conversation · actions · personal assistant","LOCAL / REMOTE"),
 HubModule("AGENT","WDA","WDA PHOTO","visual direction · photography · retouching","AGENT"),
 HubModule("AGENT","CIN","CINEMA","script → storyboard → shots → final film","MULTI-AGENT"),
 HubModule("AGENT","COD","CODING","OpenCode · repositories · terminal · tests","SANDBOX"),
 HubModule("AGENT","RES","RESEARCH","deep research · evidence · reports","WEB / LOCAL"),
 HubModule("AGENT","MCP","TOOL BUS","MCP · plugins · external tools","GATEWAY"),
 HubModule("SYSTEM","LLM","MODEL VAULT","GGUF · safetensors · LoRA · VAE · encoders","ON-DEVICE"),
 HubModule("SYSTEM","RAG","KNOWLEDGE","documents · memory · project graph · prompts","ON-DEVICE"),
 HubModule("SYSTEM","EVA","EVALUATION","Promptfoo · OmniVideoBench · regression","LAB"),
 HubModule("SYSTEM","EVO","EVOLUTION LAB","AgentOpt · DGM · OpenAlpha Evolve · OpenEvolve","ISOLATED"),
 HubModule("SYSTEM","SAFE","SAFETY","NSFW classifier · content policy · audit","ON-DEVICE"),
 HubModule("SYSTEM","COM","COMMUNICATION","voice/video · screen share · collaboration","ADAPTERS")
)

@Composable
private fun AiHubTheme(content:@Composable ()->Unit) {
 MaterialTheme(colorScheme=darkColorScheme(primary=HubGold,onPrimary=HubBg,background=HubBg,onBackground=HubIvory,surface=HubPanel,onSurface=HubIvory,onSurfaceVariant=HubMuted,outline=Color(0xFF554624)),
  content=content)
}

@Composable
private fun AiHubScreen(onOpenStudio:()->Unit) {
 var filter by remember { mutableStateOf("ALL") }
 var screen by remember { mutableStateOf("HUB") }
 var importedModels by remember { mutableStateOf(0) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris-> importedModels+=uris.size }
 val visible=modules.filter{filter=="ALL"||it.group==filter}
 if (screen == "MODELS") { ModelRuntimeScreen { screen = "HUB" }; return }
 if (screen == "PUBLISH") { SocialPublishScreen { screen = "HUB" }; return }
 Column(Modifier.fillMaxSize().background(HubBg).padding(16.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("MOJE ALTEREGO",color=HubGold,fontSize=23.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,letterSpacing=2.sp)
    Text("AI STUDIO · OMNI CREATIVE SYSTEM",color=HubIvory,fontSize=11.sp,letterSpacing=1.5.sp)
   }
   Surface(shape=RoundedCornerShape(12.dp),color=HubPanel,border=androidx.compose.foundation.BorderStroke(1.dp,HubGold)){
    Text("LOCAL-FIRST",Modifier.padding(horizontal=12.dp,vertical=9.dp),color=HubGold2,fontSize=10.sp,fontWeight=FontWeight.Bold)
   }
  }
  Spacer(Modifier.height(14.dp))
  Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=HubPanel,border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF59491F))){
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text("COMMAND CENTER",color=HubGold,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp)
    Text("Jeden interfejs dla modeli, agentów, generacji obrazu/wideo/audio, kodowania, researchu i laboratoriów ewolucyjnych.",color=HubIvory)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
     Button(onClick=onOpenStudio,colors=ButtonDefaults.buttonColors(containerColor=HubGold,contentColor=HubBg),modifier=Modifier.weight(1f)){Text("OTWÓRZ STUDIO",fontWeight=FontWeight.Bold)}
     OutlinedButton(onClick={picker.launch(arrayOf("application/octet-stream","application/*","model/*"))},modifier=Modifier.weight(1f)){Text("IMPORT MODELI")}
    }
   }
  }
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
   listOf("ALL","CREATE","AGENT","SYSTEM").forEach{item->FilterChip(selected=filter==item,onClick={filter=item},label={Text(item)})}
  }
  Spacer(Modifier.height(8.dp))
  Text("MODEL VAULT  ·  $importedModels NOWYCH PLIKÓW  ·  GGUF / SAFE-TENSORS / LORA / VAE",color=HubMuted,fontSize=10.sp,letterSpacing=1.1.sp)
  Spacer(Modifier.height(8.dp))
  LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=18.dp)){items(visible){module -> ModuleCard(module) { if (module.code == "LLM") screen = "MODELS"; if (module.code == "COM") screen = "PUBLISH" }}}
 }
}

@Composable
private fun ModuleCard(module:HubModule, onClick: () -> Unit){
 Surface(Modifier.fillMaxWidth().heightIn(min=122.dp).clickable(onClick=onClick),color=HubPanel,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF3B311B))){
  Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(module.code,color=HubGold,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text(module.runtime,color=HubMuted,fontSize=8.sp,fontWeight=FontWeight.Bold)}
   Text(module.title,color=HubIvory,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,fontSize=16.sp)
   Text(module.detail,color=Color(0xFFC5BCA9),fontSize=11.sp,lineHeight=15.sp)
  }
 }
}


@Composable
private fun ModelRuntimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Trzy sloty GGUF: CHAT+CODE, IMAGE, VIDEO.") }
    var busy by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf(0) }
    var slots by remember {
        mutableStateOf(
            listOf(
                ResidentSlot("chat-code", "CHAT + CODE", "GGUF #1"),
                ResidentSlot("image", "IMAGE", "GGUF #2"),
                ResidentSlot("video", "VIDEO", "GGUF #3")
            )
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "model.gguf"
            slots = slots.mapIndexed { index, slot ->
                if (index == selectedSlot) slot.copy(fileName = name, path = uri.toString(), status = "READY") else slot
            }
            status = "Wybrano model dla slotu " + (selectedSlot + 1)
        }
    }
    fun auth() = "Bearer " + apiKey.trim()
    fun api() = StudioApi.create(server)
    Column(Modifier.fillMaxSize().background(HubBg).padding(16.dp)) {
        HeaderRow("MODEL VAULT · 3× GGUF", onBack)
        Text("SLOT 1 CHAT + CODE · SLOT 2 IMAGE · SLOT 3 VIDEO", color = HubGold, fontWeight = FontWeight.Bold)
        Text("Wymagane jednoczesne utrzymywanie trzech rezydentnych modeli.", color = HubMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        slots.forEachIndexed { index, slot ->
            Surface(
                Modifier.fillMaxWidth().clickable { selectedSlot = index },
                color = if (selectedSlot == index) Color(0xFF19150D) else HubPanel,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, if (selectedSlot == index) HubGold else Color(0xFF3B311B))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SLOT " + (index + 1) + " · " + slot.title, color = HubGold2, fontWeight = FontWeight.Bold)
                        Text(if (slot.loaded) "LOADED" else slot.status, color = HubMuted, fontSize = 9.sp)
                    }
                    Text(slot.purpose, color = HubIvory, fontSize = 11.sp)
                    Text(if (slot.fileName.isBlank()) "Brak modelu" else slot.fileName, color = Color(0xFFC5BCA9), fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = { selectedSlot = index; picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("WYBIERZ GGUF") }
                        Button(
                            onClick = {
                                if (server.isBlank() || apiKey.isBlank() || slot.fileName.isBlank()) {
                                    status = "Uzupełnij backend, API key i model."
                                } else {
                                    scope.launch {
                                        busy = true
                                        try {
                                            api().loadRuntimeSlot(slot.id, auth(), RuntimeLoadRequest(slot.path, slot.fileName))
                                            slots = slots.mapIndexed { i, s -> if (i == index) s.copy(loaded = true, status = "LOADED") else s }
                                            status = "Slot " + (index + 1) + " załadowany."
                                        } catch (e: Exception) {
                                            status = "Błąd ładowania: " + (e.localizedMessage ?: "błąd")
                                        } finally { busy = false }
                                    }
                                }
                            },
                            enabled = !busy && slot.fileName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = HubGold, contentColor = HubBg),
                            modifier = Modifier.weight(1f)
                        ) { Text("LOAD") }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Button(
            onClick = {
                if (server.isBlank() || apiKey.isBlank()) {
                    status = "Podaj backend HTTPS i API key."
                } else scope.launch {
                    busy = true
                    try {
                        val state = api().runtimeState(auth())
                        status = "Backend potwierdził stan " + state.slots.size + " slotów."
                    } catch (e: Exception) {
                        status = "Błąd runtime: " + (e.localizedMessage ?: "błąd")
                    } finally { busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ODŚWIEŻ STAN 3 SLOTÓW") }
        Spacer(Modifier.height(10.dp))
        Text("HUGGING FACE", color = HubGold, fontWeight = FontWeight.Bold)
        Text("Backend ma wyszukiwanie repozytoriów HF oraz bezpieczny endpoint pobierania plików modeli.", color = HubMuted, fontSize = 11.sp)
        OutlinedTextField(
            value = server, onValueChange = { server = it },
            label = { Text("Backend HTTPS") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("Klucz API") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Text(status, color = HubIvory, fontSize = 11.sp)
    }
}

private data class ResidentSlot(
    val id: String,
    val title: String,
    val purpose: String,
    val fileName: String = "",
    val path: String = "",
    val loaded: Boolean = false,
    val status: String = "EMPTY"
)

@Composable
private fun SocialPublishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) shareMedia(context, uri)
    }
    Column(Modifier.fillMaxSize().background(HubBg).padding(16.dp)) {
        HeaderRow("PUBLISH · IMAGE / VIDEO", onBack)
        Spacer(Modifier.height(12.dp))
        Text("PUBLIKACJA W MEDIACH SPOŁECZNOŚCIOWYCH", color = HubGold, fontWeight = FontWeight.Bold)
        Text(
            "Wybierz wygenerowany plik. Android otworzy systemowy Sharesheet z aplikacjami, które mogą przyjąć obraz lub wideo.",
            color = HubMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { picker.launch(arrayOf("image/*", "video/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = HubGold, contentColor = HubBg),
            modifier = Modifier.fillMaxWidth()
        ) { Text("WYBIERZ IMAGE / VIDEO → PUBLIKUJ", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Hasła do serwisów społecznościowych pozostają poza aplikacją. Publikację wykonuje wybrana aplikacja docelowa.",
            color = HubMuted, fontSize = 10.sp
        )
    }
}

@Composable
private fun HeaderRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", color = HubGold, fontSize = 22.sp) }
        Text(title, color = HubIvory, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
    }
}

private fun shareMedia(context: android.content.Context, uri: Uri) {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Opublikuj / udostępnij"))
}
