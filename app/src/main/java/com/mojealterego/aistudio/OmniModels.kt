package com.mojealterego.aistudio

enum class ModelSlot(val key: String, val title: String, val subtitle: String) {
    CHAT("chat", "CHAT", "rozmowa + JARVIS"),
    CODE("code", "CODE", "coding + agent"),
    IMAGE("image", "image", "generator obrazu"),
    VIDEO("video", "video", "generator wideo")
}

data class LoadedModel(
    val slot: ModelSlot,
    val fileName: String,
    val path: String,
    val format: String = "GGUF",
    val loaded: Boolean = false,
    val memoryMb: Long? = null
)

data class HuggingFaceModelFile(
    val repoId: String,
    val fileName: String,
    val sizeBytes: Long? = null,
    val downloadUrl: String,
    val revision: String = "main"
)

data class SocialTarget(
    val id: String,
    val title: String,
    val supportsImage: Boolean,
    val supportsVideo: Boolean,
    val mode: String
)
