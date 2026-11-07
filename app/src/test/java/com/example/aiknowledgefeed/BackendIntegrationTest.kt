package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.api.PaperViewApi
import com.example.aiknowledgefeed.backend.models.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Opt-in: real HTTP against the explicitly test-only local FastAPI factory. */
class BackendIntegrationTest {
    @Test fun pdfToPersonalizedFeedAndFutureKnowledgeOverRealHttp() = runBlocking {
        val url = System.getenv("PAPERVIEW_INTEGRATION_URL")
        assumeTrue("Local mock server not enabled", url != null)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer integration-test-token").build())
        }.build()
        val api = Retrofit.Builder().baseUrl(url!!).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(PaperViewApi::class.java)
        api.profile(Profile(bachelor_subject = "Biologie", interests = listOf("Biomarker")))
        val bytes = javaClass.getResourceAsStream("/thesis.pdf")!!.use { it.readBytes() }
        val document = api.upload(MultipartBody.Part.createFormData("file", "thesis.pdf", bytes.toRequestBody("application/pdf".toMediaType())))
        assertEquals("extracted", document.status)
        val analyzed = api.analyze(document.id, Consent(true))
        val candidate = analyzed.concepts.first()
        assertEquals("candidate", candidate.status)
        val confirmed = api.update(candidate.id, KnowledgeUpdate(candidate.name, candidate.description, "confirmed"))
        assertEquals("confirmed", confirmed.status)
        val first = api.generate(Generation("Europe/Berlin"))
        assertTrue(first.items.first().prior_knowledge.any { it.id == candidate.id })
        assertTrue(first.items.first().summary.contains("Bayes"))
        assertTrue(first.items.first().abstract_based)
        val saved = api.understood(first.items.first().paper_id)
        assertTrue(saved.description.contains("Biomarker"))
        assertTrue(saved.connections.contains(candidate.id))
        assertEquals(saved.id, api.understood(first.items.first().paper_id).id)
        assertEquals(first.date, api.feed("Europe/Berlin").date)
        client.newCall(Request.Builder().url(url + "test/next-day").post(ByteArray(0).toRequestBody()).build()).execute().use { assertTrue(it.isSuccessful) }
        val second = api.generate(Generation("Europe/Berlin"))
        assertTrue(second.items.first().prior_knowledge.any { it.id == saved.id })
        assertTrue(api.knowledge().any { it.id == saved.id })
        api.deleteDocument(document.id)
        assertFalse(api.documents().any { it.id == document.id })
        assertTrue(api.knowledge().any { it.id == candidate.id })
        api.deleteKnowledge(saved.id)
        assertFalse(api.knowledge().any { it.id == saved.id })
    }
}
