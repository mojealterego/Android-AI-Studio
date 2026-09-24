package com.mojealterego.aistudio

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** DTOs aligned with backend/app/main.py and workflow_registry.py. */
data class CreateJobRequest(
    val type: String,
    val prompt: String,
    val negativePrompt: String = "",
    val workflow: Map<String, Any>
)

data class WorkflowParameter(
    val type: String,
    val required: Boolean = true,
    val default: Any? = null,
    val min_length: Int? = null,
    val max_length: Int? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val choices: List<String>? = null
)

data class WorkflowSummary(
    val id: String,
    val version: Int,
    val label: String,
    val type: String,
    val parameters: Map<String, WorkflowParameter> = emptyMap()
)

data class WorkflowListResponse(val workflows: List<WorkflowSummary> = emptyList())

data class PreviewRequest(
    val workflow_id: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val session_id: String,
    val task_id: String
)

data class ApprovalRequest(
    val workflow_id: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val session_id: String,
    val task_id: String,
    val preview_digest: String,
    val duration: String = "ONCE",
    val expires_in_seconds: Int = 300
)

data class ApprovalPreviewResponse(
    val workflow_id: String,
    val workflow_version: Int,
    val media_type: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val session_id: String,
    val task_id: String,
    val action: String,
    val resource: String,
    val preview_digest: String,
    val consequence: String
)

data class ApprovalResponse(
    val grant_id: String,
    val subject_id: String,
    val resource: String,
    val duration: String,
    val expires_at: String
)

data class CreateJobV2Request(
    val workflow_id: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val grant_id: String,
    val session_id: String,
    val task_id: String,
    val client_id: String? = null
)

data class JobOutput(
    val filename: String? = null,
    val subfolder: String? = null,
    val type: String? = null,
    val format: String? = null,
    val media_index: Int? = null,
    val media_path: String? = null
)

data class JobResponse(
    val id: String,
    val prompt_id: String? = null,
    val type: String? = null,
    val status: String,
    val progress: Double = 0.0,
    val queue_position: Int? = null,
    val outputs: List<JobOutput> = emptyList(),
    val error: String? = null
)

data class CancelJobResponse(
    val id: String,
    val cancelled: Boolean,
    val status: String,
    val action: String? = null
)

data class JobResultResponse(
    val id: String,
    val outputs: List<JobOutput> = emptyList()
)



data class RuntimeSlotState(
    val slot_id: String,
    val loaded: Boolean = false,
    val model_path: String? = null,
    val model_name: String? = null,
    val memory_mb: Int? = null
)

data class RuntimeLoadRequest(val model_path: String, val model_name: String, val memory_mb: Int? = null)\ndata class RuntimeLoadResponse(val status: String, val slot: RuntimeSlotState)\n\ndata class RuntimeStateResponse(
    val slots: List<RuntimeSlotState> = emptyList(),
    val simultaneous_resident_slots: Int = 3
)

data class HfModel(
    val id: String? = null,
    val private: Boolean = false,
    val downloads: Long? = null,
    val likes: Long? = null,
    val tags: List<String> = emptyList(),
    val url: String? = null
)

data class HfSearchResponse(val models: List<HfModel> = emptyList())\n\ndata class HfDownloadRequest(val repo_id: String, val filename: String, val revision: String = "main")\ndata class HfDownloadResponse(val status: String, val path: String, val bytes: Long, val repo_id: String, val filename: String)
\ninterface StudioApi {
    @GET("api/omni/runtime")
    suspend fun runtimeState(
        @Header("Authorization") authorization: String
    ): RuntimeStateResponse

    @POST("api/omni/runtime/{slot_id}/load")
    suspend fun loadRuntimeSlot(
        @retrofit2.http.Path("slot_id") slotId: String,
        @Header("Authorization") authorization: String,
        @Body request: RuntimeLoadRequest
    ): RuntimeLoadResponse

    @GET("api/models/hf/search")
    suspend fun searchHuggingFace(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Query("q") query: String,
        @retrofit2.http.Query("limit") limit: Int = 20
    ): HfSearchResponse

    @retrofit2.http.POST("api/models/hf/download")\n    suspend fun downloadHuggingFace(\n        @Header("Authorization") authorization: String,\n        @Body request: HfDownloadRequest\n    ): HfDownloadResponse\n\n    @GET("api/health")
    suspend fun health(): Map<String, String>

    @GET("api/v2/workflows")
    suspend fun listWorkflows(
        @Header("Authorization") authorization: String
    ): WorkflowListResponse

    @POST("api/v2/approval-preview")
    suspend fun approvalPreview(
        @Header("Authorization") authorization: String,
        @Body request: PreviewRequest
    ): ApprovalPreviewResponse

    @POST("api/v2/approvals")
    suspend fun createApproval(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String,
        @Body request: ApprovalRequest
    ): ApprovalResponse

    @POST("api/v2/jobs")
    suspend fun createJobV2(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String,
        @Body request: CreateJobV2Request
    ): JobResponse

    /** Legacy endpoint intentionally retained only for explicit server-side 410 compatibility testing. */
    @POST("api/jobs")
    suspend fun createJob(
        @Header("Authorization") authorization: String,
        @Body request: CreateJobRequest
    ): JobResponse

    @GET("api/jobs/{id}")
    suspend fun getJob(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String,
        @Path("id") id: String
    ): JobResponse

    @Streaming
    @GET("api/jobs/{id}/media/{index}")
    suspend fun getMedia(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String,
        @Path("id") id: String,
        @Path("index") index: Int
    ): ResponseBody

    @retrofit2.http.POST("api/jobs/{id}/cancel")
    suspend fun cancelJob(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String,
        @Path("id") id: String
    ): CancelJobResponse

    @GET("api/jobs/{id}/result")
    suspend fun getResult(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String,
        @Path("id") id: String
    ): JobResultResponse

    @GET("api/jobs")
    suspend fun listJobs(
        @Header("Authorization") authorization: String,
        @Header("X-Approval-Token") approvalToken: String
    ): List<JobResponse>

    companion object {
        fun openProgressWebSocket(
            baseUrl: String,
            authorization: String,
            approvalToken: String,
            jobId: String,
            listener: WebSocketListener
        ): WebSocket {
            val normalized = baseUrl.trim().removeSuffix("/")
            require(normalized.startsWith("https://", ignoreCase = true)) {
                "Backend musi używać HTTPS."
            }
            val wsBase = "wss://" + normalized.removePrefix("https://")
            val request = Request.Builder()
                .url("$wsBase/api/jobs/$jobId/progress")
                .header("Authorization", authorization)
                .header("X-Approval-Token", approvalToken)
                .build()
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
                .newWebSocket(request, listener)
        }

        fun create(baseUrl: String): StudioApi {
            val normalizedInput = baseUrl.trim()
            require(normalizedInput.startsWith("https://", ignoreCase = true)) {
                "Backend musi używać HTTPS."
            }
            val normalized = normalizedInput.trimEnd('/') + "/"
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(StudioApi::class.java)
        }
    }
}
