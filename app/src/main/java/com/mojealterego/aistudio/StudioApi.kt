package com.mojealterego.aistudio

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Wire models matching docs/backend-api.md. */
data class CreateJobRequest(
    val type: String,
    val prompt: String,
    val negativePrompt: String = "",
    val workflowId: String,
    val parameters: Map<String, String> = emptyMap()
)

data class JobResponse(
    val id: String,
    val status: String,
    val progress: Double = 0.0,
    val resultUrl: String? = null,
    val error: String? = null
)

interface StudioApi {
    @GET("api/health") suspend fun health(): Map<String, String>
    @POST("api/jobs") suspend fun createJob(
        @Header("Authorization") authorization: String,
        @Body request: CreateJobRequest
    ): JobResponse
    @GET("api/jobs/{id}") suspend fun getJob(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): JobResponse

    companion object {
        fun create(baseUrl: String): StudioApi {
            require(baseUrl.startsWith("https://")) { "Backend musi używać HTTPS." }
            val normalized = baseUrl.trimEnd('/') + "/"
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(StudioApi::class.java)
        }
    }
}
