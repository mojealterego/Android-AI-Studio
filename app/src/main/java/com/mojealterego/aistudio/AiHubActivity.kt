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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import android.net.Uri

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
 HubModule("CREATE","AUD","AUDIO LAB","Bark · AudioGen · sound design","LOCAL / GPU"),
 HubModule("CREATE","VOI","VOICE LAB","STT · TTS · dubbing · voice continuity","LOCAL / GPU"),
 HubModule("CREATE","MUS","MUSIC LAB","OpenMusic · MusicGen · MIDI Transformer","GPU WORKER"),
 HubModule("CREATE","PLG","PLUGIN HUB","MCP · apps · integrations · OAuth","GATEWAY"),
 HubModule("CREATE","AVA","AVATAR LAB","Duix · HunyuanPortrait · lip-sync","GPU WORKER"),
 HubModule("CREATE","NOV","STORY LAB","novel · screenplay · characters · continuity","AGENT"),
 HubModule("AGENT","A01","INTENT DIRECTOR","intent · ambiguity · target specification","AGENT"),
 HubModule("AGENT","A02","REFERENCE ANALYST","identity · reference roles · conflict detection","AGENT"),
 HubModule("AGENT","A03","IMAGE PRODUCTION","masks · composition · materials · lighting · edits","AGENT"),
 HubModule("AGENT","A04","MODEL PIPELINE","models · limits · formats · costs · routing","AGENT"),
 HubModule("AGENT","A05","VISUAL QA","identity drift · anatomy · continuity · specification QA","AGENT"),
 HubModule("AGENT","A06","CINEMATIC AGENT","storyboard · blocking · timeline · camera · video prompts","AGENT"),
 HubModule("SYSTEM","GOV","GOVERNANCE","authority · spend limits · receipts · revocation · credential boundary","SECURITY"),
 HubModule("AGENT","AGT","AGENT REGISTRY","A01–A06 · intent · identity · image · pipeline · QA · cinema","MULTI-AGENT"),
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
 var activeModule by remember { mutableStateOf<String?>(null) }
 var importedModels by remember { mutableStateOf(0) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris-> importedModels+=uris.size }
 val visible=modules.filter{filter=="ALL"||it.group==filter}
 if (screen == "MODELS") { ModelRuntimeScreen { screen = "HUB" }; return }
 if (screen == "PUBLISH") { SocialPublishScreen { screen = "HUB" }; return }
 activeModule?.let { code -> OmniModuleScreen(code = code, onBack = { activeModule = null }) ; return }
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
  LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=18.dp)){items(visible){module -> ModuleCard(module) { when (module.code) { "LLM" -> screen = "MODELS"; "COM" -> screen = "PUBLISH"; else -> activeModule = module.code } }}}
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
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Wymagane 3 rezydentne modele GGUF: CHAT+CODE, IMAGE, VIDEO.") }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hfModels by remember { mutableStateOf<List<HfModel>>(emptyList()) }
    var slots by remember {
        mutableStateOf(
            listOf(
                ResidentSlot("chat-code", "CHAT + CODE", "GGUF #1"),
                ResidentSlot("image", "IMAGE", "GGUF #2"),
                ResidentSlot("video", "VIDEO", "GGUF #3")
            )
        )
    }
    fun auth() = "Bearer " + apiKey.trim()
    fun api() = StudioApi.create(server)
    Column(Modifier.fillMaxSize().background(HubBg).padding(16.dp)) {
        HeaderRow("MODEL VAULT · 3× GGUF", onBack)
        Text("TRZY MODELE REZYDENTNE", color=HubGold, fontWeight=FontWeight.Bold)
        Text("Sloty są ładowane atomowo; konfiguracja z mniej niż trzema modelami nie jest gotowa.", color=HubMuted, fontSize=12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(server, { server=it }, label={Text("Backend HTTPS")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(apiKey, { apiKey=it }, label={Text("API key")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Spacer(Modifier.height(8.dp))
        slots.forEachIndexed { index, slot ->
            Surface(Modifier.fillMaxWidth(), color=HubPanel, shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp, if (slot.fileName.isNotBlank()) HubGold else Color(0xFF3B311B))) {
                Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("SLOT "+(index+1)+" · "+slot.title, color=HubGold2, fontWeight=FontWeight.Bold)
                        Text(if(slot.loaded) "LOADED" else slot.status, color=HubMuted, fontSize=9.sp)
                    }
                    Text(slot.purpose, color=HubIvory, fontSize=11.sp)
                    Text(if(slot.fileName.isBlank()) "Nie wybrano GGUF" else slot.fileName, color=Color(0xFFC5BCA9), fontSize=10.sp)
                    Text("Ścieżka: "+if(slot.path.isBlank()) "brak" else slot.path, color=HubMuted, fontSize=9.sp)
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Button(onClick={
            if(server.isBlank() || apiKey.isBlank() || slots.any { it.fileName.isBlank() }) {
                status="Nie można uruchomić runtime: wszystkie 3 sloty muszą mieć GGUF."
            } else scope.launch {
                busy=true
                try {
                    api().loadAllRuntime(auth(), LoadAllRuntimeRequest(slots.map { ResidentModelRequest(it.path,it.fileName) }))
                    slots=slots.map { it.copy(loaded=true,status="LOADED") }
                    status="3/3 modeli zostały zarejestrowane jako rezydentne."
                } catch(e:Exception) { status="Błąd ładowania 3 slotów: "+(e.localizedMessage ?: "błąd") }
                finally { busy=false }
            }
        }, enabled=!busy, colors=ButtonDefaults.buttonColors(containerColor=HubGold, contentColor=HubBg),
            modifier=Modifier.fillMaxWidth()) { Text("LOAD 3 MODELE JEDNOCZEŚNIE", fontWeight=FontWeight.Bold) }

        Spacer(Modifier.height(12.dp))
        Text("HUGGING FACE MODEL VAULT", color=HubGold, fontWeight=FontWeight.Bold)
        Text("Wyszukuj repozytoria i wybieraj konkretne pliki GGUF do pobrania na backend.", color=HubMuted, fontSize=11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(query,{query=it},label={Text("Szukaj modelu")},modifier=Modifier.weight(1f),singleLine=true)
            Button(onClick={
                if(server.isBlank()||apiKey.isBlank()) status="Podaj backend i API key."
                else scope.launch {
                    busy=true
                    try { hfModels=api().searchHuggingFace(auth(),query,20).models; status="Znaleziono "+hfModels.size+" repozytoriów." }
                    catch(e:Exception){ status="Błąd HF: "+(e.localizedMessage ?: "błąd") }
                    finally { busy=false }
                }
            },enabled=!busy){Text("SZUKAJ")}
        }
        Spacer(Modifier.height(7.dp))
        hfModels.take(8).forEach { model ->
            Surface(Modifier.fillMaxWidth(),color=HubPanel,shape=RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(model.id ?: "repo",color=HubIvory,fontWeight=FontWeight.Bold,fontSize=12.sp)
                    model.files.filter { it.filename.lowercase().endsWith(".gguf") }.take(6).forEach { file ->
                        TextButton(onClick={
                            val repo=model.id
                            if(server.isBlank()||apiKey.isBlank()||repo==null) status="Brak danych pobierania."
                            else scope.launch {
                                busy=true
                                try {
                                    val result=api().downloadHuggingFace(auth(),HfDownloadRequest(repo,file.filename))
                                    status="Pobrano "+result.filename+" ("+result.bytes+" B)."
                                    slots=slots.mapIndexed { i,s -> if(s.fileName.isBlank()) s.copy(fileName=file.filename,path=result.path,status="READY") else s }
                                } catch(e:Exception){status="Błąd pobierania: "+(e.localizedMessage ?: "błąd")}
                                finally {busy=false}
                            }
                        }){Text("↓ "+file.filename,color=HubGold2,fontSize=10.sp)}
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        Text(status,color=HubIvory,fontSize=11.sp)
    }
}

@Composable
private fun SocialPublishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> selectedUri = uri }
    val targets = listOf(
        "INSTAGRAM" to "com.instagram.android",
        "FACEBOOK" to "com.facebook.katana",
        "TIKTOK" to "com.zhiliaoapp.musically",
        "YOUTUBE" to "com.google.android.youtube",
        "X" to "com.twitter.android"
    )
    fun publish(packageName: String?) {
        val uri = selectedUri ?: return
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageName != null) setPackage(packageName)
        }
        try { context.startActivity(intent) } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Opublikuj / udostępnij"))
        }
    }
    Column(Modifier.fillMaxSize().background(HubBg).padding(16.dp)) {
        HeaderRow("PUBLISH · IMAGE / VIDEO", onBack)
        Text("MEDIA PUBLISHING", color=HubGold, fontWeight=FontWeight.Bold)
        Text("Przekazanie wygenerowanego obrazu lub filmu do zainstalowanej aplikacji społecznościowej.", color=HubMuted, fontSize=11.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick={picker.launch(arrayOf("image/*","video/*"))},modifier=Modifier.fillMaxWidth()) {
            Text(if(selectedUri==null) "WYBIERZ IMAGE / VIDEO" else "ZMIENIĆ PLIK")
        }
        OutlinedTextField(caption,{caption=it},label={Text("Opis / caption")},modifier=Modifier.fillMaxWidth(),minLines=3)
        Spacer(Modifier.height(8.dp))
        targets.forEach { (label,pkg) ->
            Button(onClick={publish(pkg)},enabled=selectedUri!=null,modifier=Modifier.fillMaxWidth(),
                colors=ButtonDefaults.buttonColors(containerColor=HubGold,contentColor=HubBg)) {
                Text("PUBLIKUJ → "+label,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
        }
        OutlinedButton(onClick={publish(null)},enabled=selectedUri!=null,modifier=Modifier.fillMaxWidth()) {
            Text("INNE APLIKACJE / SYSTEMOWY SHARE")
        }
        Spacer(Modifier.height(8.dp))
        Text("Logowanie i poświadczenia pozostają w aplikacji docelowej. AI Studio przekazuje media przez Android Intent.",color=HubMuted,fontSize=10.sp)
    }
}

@Composable
private fun HeaderRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=onBack){Text("←",color=HubGold,fontSize=22.sp)}
        Text(title,color=HubIvory,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif)
    }
}


private data class ResidentSlot(val slotId: String, val title: String, val purpose: String, val fileName: String = "", val path: String = "", val loaded: Boolean = false, val status: String = "EMPTY")

@Composable
private fun OmniModuleScreen(code: String, onBack: () -> Unit) {
    val module = modules.firstOrNull { it.code == code }
    Column(Modifier.fillMaxSize().background(HubBg).padding(16.dp)) {
        HeaderRow(module?.title ?: code, onBack)
        Spacer(Modifier.height(8.dp))
        Surface(Modifier.fillMaxWidth(), color = HubPanel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF59491F))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(module?.detail ?: "OMNI MODULE", color = HubIvory, fontSize = 15.sp)
                Text("RUNTIME · " + (module?.runtime ?: "AGENT"), color = HubGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Module shell connected to the unified Command Center. Runtime adapters are activated only when their backend contract is verified.", color = HubMuted)
            }
        }
    }
}
