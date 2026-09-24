package com.mojealterego.aistudio

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ModuleBg = Color(0xFF070706)
private val ModulePanel = Color(0xFF12110E)
private val ModuleGold = Color(0xFFD4AF37)
private val ModuleGold2 = Color(0xFFF0D77A)
private val ModuleText = Color(0xFFF4F0E6)
private val ModuleMuted = Color(0xFFA89B7A)

data class OmniModuleSpec(
    val code: String,
    val title: String,
    val subtitle: String,
    val runtime: String,
    val capabilities: List<String>,
    val actions: List<String>
)

fun moduleSpec(code: String): OmniModuleSpec = when (code) {
    "IMG" -> OmniModuleSpec("IMG","IMAGE LAB","Generowanie i edycja obrazu","LOCAL / GPU",
        listOf("GGUF / diffusion","Flux · SDXL · Qwen · LoRA","ControlNet / inpaint / img2img","WDA Ω∞ reference control","Visual QA przed eksportem"),
        listOf("NOWA GENERACJA","DODAJ REFERENCJE","OTWÓRZ MODEL VAULT"))
    "VID" -> OmniModuleSpec("VID","VIDEO LAB","Generowanie klipów i ciągłości scen","GPU WORKER",
        listOf("Wan · LTX · Veo · Kling / router","Image-to-video / reference-to-video","First / last frame i extend","Actor / character continuity","Timeline do 124 minut"),
        listOf("NOWY KLIP","CHARACTER LOCK","OTWÓRZ CINEMA"))
    "AUD" -> OmniModuleSpec("AUD","AUDIO LAB","Dźwięk, efekty i postprodukcja","LOCAL / GPU",
        listOf("TTS / STT","Bark · AudioGen · Tortoise","Voice cleanup / enhancement","SFX i ambience","Eksport WAV / MP3 / FLAC"),
        listOf("GENERUJ AUDIO","TRANSKRYBUJ","DODAJ DO PROJEKTU"))
    "VOI" -> OmniModuleSpec("VOI","VOICE LAB","Głos, dubbing i narracja","LOCAL / GPU",
        listOf("Speech-to-text","Text-to-speech","Voice reference / voice profile","Dubbing scen i synchronizacja","Napisy + timestampy"),
        listOf("NOWY VOICEOVER","TRANSKRYPCJA","DUBBING"))
    "MUS" -> OmniModuleSpec("MUS","MUSIC LAB","Muzyka, soundtrack i MIDI","GPU WORKER",
        listOf("OpenMusic / MusicGen","Giant Music Transformer","MIDI generation / continuation","Stem / soundtrack workflow","Sync do timeline filmu"),
        listOf("GENERUJ MUZYKĘ","GENERUJ MIDI","DODAJ DO TIMELINE"))
    "AVA" -> OmniModuleSpec("AVA","AVATAR LAB","Cyfrowi aktorzy i lip-sync","GPU WORKER",
        listOf("Duix / digital human","Reference actor","Lip-sync","Facial / pose continuity","Voice + video composition"),
        listOf("NOWY AKTOR","LIP-SYNC","GENERUJ UJĘCIE"))
    "NOV" -> OmniModuleSpec("NOV","STORY LAB","Scenariusz, postacie i ciągłość","AGENT",
        listOf("Story bible","Character bible","Scene graph","Continuity memory","Script → shot list"),
        listOf("NOWA HISTORIA","CHARACTER BIBLE","GENERUJ SCENES"))
    "JAR" -> OmniModuleSpec("JAR","JARVIS","Agent rozmowy, pamięci i działań","LOCAL / REMOTE",
        listOf("CHAT GGUF #1","Tool calling","Project memory","Action approvals","Action receipts"),
        listOf("NOWA ROZMOWA","WYKONAJ ZADANIE","HISTORIA"))
    "WDA" -> OmniModuleSpec("WDA","WDA PHOTO","Wirtualny dyrektor artystyczny","AGENT",
        listOf("Reference graph","Identity firewall","Zero-drift editing","Camera / light / material logic","Visual validation gate"),
        listOf("NOWY PROJEKT","DODAJ REFERENCJE","WALIDUJ"))
    "CIN" -> OmniModuleSpec("CIN","CINEMA","Pełna produkcja filmu","MULTI-AGENT",
        listOf("Script → storyboard → blocking","Actor continuity","Shot generation","Voice + music + SFX","Final timeline ≤ 124 min"),
        listOf("NOWY FILM","SCENE BUILDER","RENDER FINAL"))
    "COD" -> OmniModuleSpec("COD","CODING","Kodowanie, repozytoria i testy","SANDBOX",
        listOf("CHAT+CODE GGUF #1","OpenCode workflows","GitHub / GitLab","Build / test / diagnostics","Patch + action receipt"),
        listOf("NOWY WORKSPACE","OTWÓRZ REPO","URUCHOM TESTY"))
    "RES" -> OmniModuleSpec("RES","RESEARCH","Głębokie badania i raporty","WEB / LOCAL",
        listOf("Web research","Source collection","Evidence graph","Report generation","Citation-aware results"),
        listOf("NOWE BADANIE","DODAJ ŹRÓDŁA","GENERUJ RAPORT"))
    "MCP" -> OmniModuleSpec("MCP","TOOL BUS","Jedna brama dla narzędzi i wtyczek","GATEWAY",
        listOf("MCP servers","Connected apps","Tool discovery","Permission scopes","Credential isolation"),
        listOf("DODAJ WTYCZKĘ","ZARZĄDZAJ UPRAWNIENIAMI","TESTUJ TOOL"))
    "PLG" -> OmniModuleSpec("PLG","PLUGIN HUB","Miejsce na wszystkie aplikacje i wtyczki","GATEWAY",
        listOf("Katalog integracji","Wtyczki AI / media / storage","OAuth poza kontekstem modelu","Enable / disable bez przebudowy projektu","Action receipt dla działań"),
        listOf("PRZEGLĄDAJ WTYCZKI","DODAJ INTEGRACJĘ","UPRAWNIENIA"))
    "LLM" -> OmniModuleSpec("LLM","MODEL VAULT","Trzy modele GGUF rezydentne + Hugging Face","ON-DEVICE",
        listOf("CHAT + CODE · GGUF #1","IMAGE · GGUF #2","VIDEO · GGUF #3","Hugging Face download","Atomic 3-slot runtime"),
        listOf("ZAŁADUJ 3 MODELE","HUGGING FACE","RUNTIME STATUS"))
    "RAG" -> OmniModuleSpec("RAG","KNOWLEDGE","Dokumenty, pamięć i graf wiedzy","ON-DEVICE",
        listOf("Local documents","RAG / embeddings","Project knowledge","Prompt library","Knowledge graph"),
        listOf("IMPORT DOKUMENTÓW","NOWY ZBIÓR","PRZESZUKAJ WIEDZĘ"))
    "EVA" -> OmniModuleSpec("EVA","EVALUATION","Testy jakości modeli i agentów","LAB",
        listOf("Promptfoo","Regression suites","Visual QA","Video continuity checks","Pass / fail gates"),
        listOf("NOWY TEST","URUCHOM EVAL","RAPORT"))
    "EVO" -> OmniModuleSpec("EVO","EVOLUTION LAB","Optymalizacja agentów i kodu","ISOLATED",
        listOf("AgentOpt","OpenAlpha Evolve","Prompt optimization","Experiment tracking","Isolated execution"),
        listOf("NOWY EKSPERYMENT","OPTYMALIZUJ","PORÓWNAJ WYNIKI"))
    "SAFE" -> OmniModuleSpec("SAFE","SAFETY","Klasyfikacja, polityki i audyt","ON-DEVICE",
        listOf("On-device classifier","Input / output screening","Audit trail","Policy gates","Human approval path"),
        listOf("SKANUJ ASSET","SPRAWDŹ PROJEKT","AUDYT"))
    "COM" -> OmniModuleSpec("COM","COMMUNICATION","Publikacja i współpraca","ADAPTERS",
        listOf("Image / video publishing","Android share adapters","OAuth connector boundary","Caption / metadata","Publishing receipts"),
        listOf("PUBLISH MEDIA","ZARZĄDZAJ KANAŁAMI","HISTORIA PUBLIKACJI"))
    else -> OmniModuleSpec(code, code, "Moduł AI Studio", "SYSTEM", emptyList(), listOf("OTWÓRZ"))
}

@Composable
fun OmniModuleScreen(code: String, onBack: () -> Unit, onPrimaryAction: (String) -> Unit = {}) {
    val spec = moduleSpec(code)
    var status by remember { mutableStateOf("READY") }
    Column(Modifier.fillMaxSize().background(ModuleBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← HUB", color = ModuleGold) }
            Spacer(Modifier.width(6.dp))
            Text(spec.code, color = ModuleGold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(spec.title, color = ModuleText, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Text(spec.subtitle, color = ModuleMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(), color = ModulePanel, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF59491F))) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("RUNTIME · "+spec.runtime, color = ModuleGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                spec.capabilities.forEach { capability ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = ModuleGold, fontSize = 8.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(capability, color = ModuleText, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("ACTIONS", color = ModuleGold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(spec.actions.size) { index ->
                val action = spec.actions[index]
                Button(onClick = {
                    status = "QUEUED · $action"
                    onPrimaryAction(action)
                }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (index == 0) ModuleGold else ModulePanel,
                        contentColor = if (index == 0) ModuleBg else ModuleText),
                    border = if (index == 0) null else BorderStroke(1.dp, Color(0xFF3B311B))) {
                    Text(action, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF0D0C0A), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("STATUS", color = ModuleGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(status, color = ModuleText, fontSize = 11.sp)
                        Text("Autonomia, limity kosztów i poświadczenia są egzekwowane poza modelem. Cofnięcie delegacji nie wymaga rekonfiguracji projektu.", color = ModuleMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}
