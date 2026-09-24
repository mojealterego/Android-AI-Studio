package com.mojealterego.aistudio

/**
 * Runtime contract for the single-app AI Studio.
 *
 * The UI may configure these values, but authorization, spending and credentials
 * must be enforced by the backend/native runtime, never by model-generated text.
 */
data class OmniModelSlot(
    val id: String,
    val role: ModelRole,
    val ggufUri: String? = null,
    val loaded: Boolean = false
)

enum class ModelRole {
    CHAT,
    CODING,
    VISION_GENERATION
}

data class OmniRuntimeConfig(
    val slots: List<OmniModelSlot> = listOf(
        OmniModelSlot("chat", ModelRole.CHAT),
        OmniModelSlot("coding", ModelRole.CODING),
        OmniModelSlot("vision-generation", ModelRole.VISION_GENERATION)
    ),
    val maxFilmDurationSeconds: Long = 124L * 60L
) {
    init {
        require(slots.map { it.role }.distinct().size == 3) {
            "Exactly three independent GGUF roles are required."
        }
        require(slots.size == 3) { "Runtime requires exactly three model slots." }
    }
}

data class HuggingFaceModelRef(
    val repoId: String,
    val revision: String? = null,
    val filename: String? = null,
    val format: String = "GGUF"
)

enum class PublishChannel {
    INSTAGRAM,
    FACEBOOK,
    YOUTUBE,
    TIKTOK,
    X,
    LINKEDIN
}

data class PublishRequest(
    val mediaUri: String,
    val channels: Set<PublishChannel>,
    val caption: String = "",
    val scheduledAt: String? = null
)

data class ActionReceipt(
    val operationId: String,
    val startedAt: String,
    val completedAt: String?,
    val permission: String,
    val result: String,
    val reversible: Boolean
)

data class AuthorityPolicy(
    val autonomyLevel: Int,
    val allowExternalPublish: Boolean,
    val allowModelDownloads: Boolean,
    val maxFilmDurationSeconds: Long = 124L * 60L
) {
    init {
        require(autonomyLevel in 0..3)
        require(maxFilmDurationSeconds <= 124L * 60L)
    }
}
