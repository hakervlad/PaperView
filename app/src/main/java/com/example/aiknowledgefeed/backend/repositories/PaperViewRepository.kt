package com.example.aiknowledgefeed.backend.repositories

import android.content.Context
import android.net.Uri
import com.example.aiknowledgefeed.BuildConfig
import com.example.aiknowledgefeed.backend.api.PaperViewApi
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.SecureStore
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PaperViewRepository(val store: SecureStore) {
    fun api(settings: Settings): PaperViewApi {
        com.example.aiknowledgefeed.backend.services.ConnectionPolicy.validate(settings, BuildConfig.DEBUG)
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            val request = chain.request(); val path = request.url.encodedPath
            val llm = path.endsWith("/generate") || path.endsWith("/analyze") || path.endsWith("/llm/test")
            val builder = request.newBuilder().header("Authorization", "Bearer ${settings.token}")
            if (llm) {
                if (settings.apiKey.isNotBlank()) {
                    check(request.url.isHttps) { "Ein eigener KI-Schlüssel darf ausschließlich über HTTPS übertragen werden." }
                    builder.header("X-LLM-Key", settings.apiKey)
                }
                if (settings.model.isNotBlank()) builder.header("X-LLM-Model", settings.model)
                if (settings.llmBaseUrl.isNotBlank()) builder.header("X-LLM-Base-URL", settings.llmBaseUrl)
            }
            chain.proceed(builder.build())
        }.build()
        return Retrofit.Builder().baseUrl(settings.backendUrl.trimEnd('/') + "/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(PaperViewApi::class.java)
    }
    suspend fun upload(api: PaperViewApi, context: Context, uri: Uri): Document {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val size = stream.read(buffer); if (size < 0) break; require(out.size() + size <= 10 * 1024 * 1024) { "PDF darf höchstens 10 MB groß sein." }; out.write(buffer, 0, size) }; out.toByteArray()
        } ?: error("PDF konnte nicht geöffnet werden.")
        require(bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-") { "Bitte eine gültige PDF-Datei auswählen." }
        val filename = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringAfterLast('/')?.substringAfterLast('\\')?.filter { it != '\r' && it != '\n' && it != '"' }?.take(120) ?: "document.pdf"
        return api.upload(MultipartBody.Part.createFormData("file", filename, bytes.toRequestBody("application/pdf".toMediaType())))
    }
}
