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
 var importedModels by remember { mutableStateOf(0) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris-> importedModels+=uris.size }
 val visible=modules.filter{filter=="ALL"||it.group==filter}
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
  LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=18.dp)){items(visible){ModuleCard(it)}}
 }
}

@Composable
private fun ModuleCard(module:HubModule){
 Surface(Modifier.fillMaxWidth().heightIn(min=122.dp).clickable{},color=HubPanel,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF3B311B))){
  Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(module.code,color=HubGold,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text(module.runtime,color=HubMuted,fontSize=8.sp,fontWeight=FontWeight.Bold)}
   Text(module.title,color=HubIvory,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,fontSize=16.sp)
   Text(module.detail,color=Color(0xFFC5BCA9),fontSize=11.sp,lineHeight=15.sp)
  }
 }
}
