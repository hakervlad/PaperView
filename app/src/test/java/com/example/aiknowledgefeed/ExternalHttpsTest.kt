package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.models.Settings
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ExternalHttpsTest {
    private fun fixture(certificateHost: String = "localhost"): Pair<MockWebServer, OkHttpClient> {
        val cert = HeldCertificate.Builder().commonName(certificateHost).addSubjectAlternativeName(certificateHost).build()
        val server = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        return MockWebServer().apply { useHttps(server.sslSocketFactory(), false) } to OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).readTimeout(300, TimeUnit.MILLISECONDS).callTimeout(3, TimeUnit.SECONDS).followRedirects(false).build()
    }
    @Test fun shippingClientsRejectUntrustedCertificatesAndWrongHostnames() = runBlocking {
        val (untrusted, _) = fixture()
        untrusted.use { server ->
            val settings = Settings(credentialEpoch = 20261109, apiKey = "fixture", model = "fixture", llmBaseUrl = server.url("/v1").toString(), directApiConsent = true)
            try { DirectModelClient().complete(settings, "JSON", JsonObject()); fail("Untrusted certificate accepted") }
            catch (e: ConnectionFailure) { assertEquals("llm_tls", e.code) }
            try { ScientificSources(openAlexEndpoint = server.url("/works").toString()).openAlex("Bayes", LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-08")); fail("Untrusted source certificate accepted") }
            catch (e: ConnectionFailure) { assertEquals("source_unavailable", e.code) }
            assertEquals(0, server.requestCount)
        }
        val (wrongHost, client) = fixture("different.invalid")
        wrongHost.use { server ->
            val settings = Settings(credentialEpoch = 20261109, apiKey = "fixture", model = "fixture", llmBaseUrl = server.url("/v1").toString(), directApiConsent = true)
            try { DirectModelClient(client).complete(settings, "JSON", JsonObject()); fail("Wrong certificate hostname accepted") }
            catch (e: ConnectionFailure) { assertEquals("llm_tls", e.code) }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun paperSourceHttpFailuresAndResponsesAreHandledWithoutModelCredentials() = runBlocking {
        val (server, client) = fixture()
        server.use {
            val sources = ScientificSources(client, server.url("/works").toString(), server.url("/arxiv").toString())
            for ((status, code) in listOf(429 to "source_rate_limit", 503 to "source_unavailable", 200 to "source_invalid")) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
                try { sources.openAlex("Bayes", LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-08")); fail(code) }
                catch (e: ConnectionFailure) { assertEquals(code, e.code) }
                val request = server.takeRequest(); assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("X-LLM-Key"))
                assertTrue(request.path!!.contains("from_publication_date"))
            }
            server.enqueue(MockResponse().setBody("{\"results\":[]}"))
            assertTrue(sources.openAlex("Bayes", LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-08")).isEmpty())
        }
    }
    @Test fun directModelRequiresExplicitConsentAndRejectsMalformedResponsesAndTimeouts() = runBlocking {
        val (server, client) = fixture()
        server.use {
            val settings = Settings(credentialEpoch = 20261109, apiKey = "fixture", model = "fixture", llmBaseUrl = server.url("/v1").toString())
            try { DirectModelClient(client).complete(settings, "JSON", JsonObject()); fail("Consent") }
            catch (e: ConnectionFailure) { assertEquals("direct_consent_required", e.code) }
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("{\"choices\":[]}"))
            try { DirectModelClient(client).complete(settings.copy(directApiConsent = true), "JSON", JsonObject()); fail("Invalid response") }
            catch (e: ConnectionFailure) { assertEquals("llm_invalid_json", e.code) }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try { DirectModelClient(client).complete(settings.copy(directApiConsent = true), "JSON", JsonObject()); fail("Timeout") }
            catch (e: ConnectionFailure) { assertEquals("llm_timeout", e.code) }
        }
    }
}
