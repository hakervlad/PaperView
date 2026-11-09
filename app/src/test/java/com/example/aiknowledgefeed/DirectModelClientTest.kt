package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.models.Settings
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class DirectModelClientTest {
    private fun secureServer(): Pair<MockWebServer, OkHttpClient> {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        return MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false) } to OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
    }
    @Test fun directHttpsRequestUsesUserKeyAndModelWithoutFastApi() = runBlocking {
        val (server, client) = secureServer()
        server.use {
            server.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"))
            val settings = Settings(credentialEpoch = 20261109, directApiConsent = true, model = "fixture-model", llmBaseUrl = server.url("/v1").toString(), apiKey = "fixture-key")
            val result = DirectModelClient(client).complete(settings, "Gib JSON zurück", JsonObject())
            assertTrue(result["ok"].asBoolean)
            val request = server.takeRequest()
            assertEquals("Bearer fixture-key", request.getHeader("Authorization")); assertNull(request.getHeader("X-LLM-Key"))
            assertEquals("/v1/chat/completions", request.path)
            assertTrue(request.body.readUtf8().contains("fixture-model"))
        }
    }
    @Test fun legacyCredentialsAreBlockedBeforeAnyRequest() = runBlocking {
        MockWebServer().use { server ->
            try {
                DirectModelClient().complete(Settings(directApiConsent=true,model="fixture",apiKey="fixture-old",llmBaseUrl=server.url("/v1").newBuilder().scheme("https").build().toString()),"JSON",JsonObject())
                fail("Legacy credentials must remain blocked")
            } catch(e:ConnectionFailure) { assertEquals("key_rotation_required",e.code) }
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun plainHttpIsRejectedBeforeSendingKey() = runBlocking {
        MockWebServer().use { server ->
            try {
                DirectModelClient().complete(Settings(credentialEpoch = 20261109, directApiConsent = true, model = "fixture", apiKey = "fixture", llmBaseUrl = server.url("/v1").toString()), "JSON", JsonObject()); fail("HTTP must be rejected")
            } catch (e: ConnectionFailure) { assertEquals("https_required", e.code) }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun providerErrorsAreDistinctAndNeverExposeResponseText() = runBlocking {
        val (server, client) = secureServer()
        server.use {
            for ((status, provider, expected) in listOf(Triple(401,"invalid_api_key","llm_invalid_key"), Triple(403,"permission","llm_forbidden"), Triple(429,"insufficient_quota","llm_insufficient_quota"), Triple(429,"rate_limit_exceeded","llm_rate_limit"), Triple(404,"model_not_found","llm_model_unavailable"), Triple(500,"failure","llm_server_error"))) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("{\"error\":{\"code\":\"$provider\",\"message\":\"PRIVATE_KEY_MUST_NOT_APPEAR\"}}"))
                try {
                    DirectModelClient(client).complete(Settings(credentialEpoch = 20261109, directApiConsent = true, model = "fixture", apiKey = "fixture", llmBaseUrl = server.url("/v1").toString()), "JSON", JsonObject()); fail("Expected provider error")
                } catch (e: ConnectionFailure) { assertEquals(expected, e.code); assertFalse(FailureMessages.message(e.code).contains("PRIVATE_KEY")) }
            }
        }
    }
}
