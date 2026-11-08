package com.example.aiknowledgefeed

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aiknowledgefeed.backend.api.PaperViewApi
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Opt-in, isolated test application only. Real TLS/HTTP/SQLite/PDF, explicit model/source fixtures. */
@RunWith(AndroidJUnit4::class)
class BackendDeviceIntegrationTest {
    @Test fun pdfAndPersonalizedKnowledgeSurviveApplicationAndServerRestart() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("backend_url")
        assumeTrue("Opt-in HTTPS integration server not configured", url != null)
        require(BuildConfig.APPLICATION_ID.endsWith(".regression"))
        require(url!!.startsWith("https://"))
        // Trust one explicit test CA, retain normal certificate chain/hostname verification.
        // This trust store exists only in androidTest, never in the shipped application.
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(
            android.util.Base64.decode(args.getString("backend_test_ca"), android.util.Base64.NO_WRAP).inputStream())
        val keys = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture", cert) }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
        val trust = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = SecureStore(app)
        val create = args.getString("backend_phase") == "create"
        if (create) {
            store.clearCache()
            SettingsPersistence(store).save(Settings(backendUrl = url, token = "integration-test-token", provider = "Explicit test model", model = "test-fixture", apiKey = "fixture-not-a-real-key"))
        }
        val settings = SettingsPersistence(SecureStore(app)).load()
        assertEquals(url, settings.backendUrl)
        assertEquals("fixture-not-a-real-key", settings.apiKey)
        val client = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
            .followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
                val req = chain.request(); val b = req.newBuilder().header("Authorization", "Bearer ${settings.token}")
                if (req.url.encodedPath.endsWith("/test") || req.url.encodedPath.endsWith("/generate") || req.url.encodedPath.endsWith("/analyze")) {
                    check(req.url.isHttps); b.header("X-LLM-Key", settings.apiKey).header("X-LLM-Model", settings.model)
                }
                chain.proceed(b.build())
            }.build()
        val api = Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(PaperViewApi::class.java)
        assertTrue(ConnectionDiagnostics(api).run { }.all { it.status == "Erfolgreich" })
        if (create) {
            val profile = api.profile(Profile(bachelor_subject = "Biologie", interests = listOf("Biomarker")))
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("thesis.pdf").use { it.readBytes() }
            val document = api.upload(MultipartBody.Part.createFormData("file", "test-thesis.pdf", bytes.toRequestBody("application/pdf".toMediaType())))
            assertEquals("extracted", document.status)
            val candidate = api.analyze(document.id, Consent(true)).concepts.single()
            assertEquals("candidate", candidate.status)
            assertFalse(api.knowledge().any { it.id == candidate.id && it.status == "confirmed" })
            val known = api.update(candidate.id, KnowledgeUpdate(candidate.name, candidate.description, "confirmed"))
            val feed = api.generate(Generation("Europe/Berlin"))
            assertTrue(feed.items.first().prior_knowledge.any { it.id == known.id })
            assertTrue(feed.items.first().summary.contains("Bayes"))
            val saved = api.understood(feed.items.first().paper_id)
            assertTrue(saved.connections.contains(known.id))
            store.snapshot(Snapshot(feed, api.knowledge(), profile, api.documents()))
            assertTrue(SecureStore(app).snapshot().knowledge.any { it.id == saved.id })
        } else {
            // External runner has stopped/restarted both the app process and uvicorn.
            val cache = SecureStore(app).snapshot()
            val saved = cache.knowledge.single { it.source_paper_id != null }
            assertEquals("Biologie", api.profile().bachelor_subject)
            assertTrue(api.documents().any { it.id == cache.documents.single().id })
            assertTrue(api.knowledge().any { it.id == saved.id })
            assertEquals(saved.id, api.understood(saved.source_paper_id!!).id)
            assertEquals(cache.knowledge.size, api.knowledge().size)
            assertEquals(cache.feed.items, api.feed("Europe/Berlin").items)
            client.newCall(Request.Builder().url(url + "test/next-day").header("Authorization", "Bearer ${settings.token}").post(ByteArray(0).toRequestBody()).build()).execute().use { assertTrue(it.isSuccessful) }
            val next = api.generate(Generation("Europe/Berlin"))
            assertTrue(next.items.first().prior_knowledge.any { it.id == saved.id })
            assertTrue(next.items.first().summary.contains(saved.name))
            assertEquals(1, api.knowledge().count { it.id == saved.id })
        }
    }
}
