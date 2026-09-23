package com.mojealterego.aistudio

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
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

data class CreateJobV2Request(
    val workflow_id: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val grant_id: String = "",
    val session_id: String = "",
    val task_id: String = "",
    val client_id: String? = null
)

data class JobOutput(
    val filename: String? = null,
    val subfolder: String? = null,
    val type: String? = null,
    val format: String? = null
)

data class JobResponse(
    val id: String,
    val prompt_id: String? = null,
    val type: String? = null,
    val status: String,
    val progress: Double = 0.0,
    val outputs: List<JobOutput> = emptyList(),
    val error: String? = null
)

data class JobResultResponse(
    val id: String,
    val outputs: List<JobOutput> = emptyList()
)

interface StudioApi {
    @GET("api/health")
    suspend fun health(): Map<String, String>

    @GET("api/v2/workflows")
    suspend fun listWorkflows(
        @Header("Authorization") authorization: String
    ): WorkflowListResponse

    @POST("api/v2/jobs")
    suspend fun createJobV2(
        @Header("Authorization") authorization: String,
        @Body request: CreateJobV2Request
    ): JobResponse

    /** Legacy endpoint; migrate callers to createJobV2. */
    @POST("api/jobs")
    suspend fun createJob(
        @Header("Authorization") authorization: String,
        @Body request: CreateJobRequest
    ): JobResponse

    @GET("api/jobs/{id}")
    suspend fun getJob(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): JobResponse

    @GET("api/jobs/{id}/result")
    suspend fun getResult(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): JobResultResponse

    @GET("api/jobs")
    suspend fun listJobs(
        @Header("Authorization") authorization: String
    ): List<JobResponse>

    companion object {
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
