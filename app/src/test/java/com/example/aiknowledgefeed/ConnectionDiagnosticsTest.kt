package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.api.PaperViewApi
import com.example.aiknowledgefeed.backend.services.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ConnectionDiagnosticsTest {
    private fun api(server: MockWebServer) = Retrofit.Builder().baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(PaperViewApi::class.java)
    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun successfulTestRequiresHealthAuthenticationAndActualModelResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("{\"ok\":true}")); server.enqueue(json("{}")); server.enqueue(json("{\"ok\":true,\"message\":\"ok\"}"))
            val steps = ConnectionDiagnostics(api(server)).run { }
            assertTrue(steps.all { it.status == "Erfolgreich" })
            assertEquals("/health", server.takeRequest().path)
            assertEquals("/v1/profile", server.takeRequest().path)
            val model = server.takeRequest(); assertEquals("POST", model.method); assertEquals("/v1/llm/test", model.path)
        }
    }
    @Test fun backendAuthenticationFailureNeverClaimsModelWasReached() = runBlocking {
        for (status in listOf(401, 403, 503)) MockWebServer().use { server ->
            server.enqueue(json("{\"ok\":true}")); server.enqueue(json("{}", status))
            val steps = ConnectionDiagnostics(api(server)).run { }
            assertEquals("Erfolgreich", steps[0].status); assertEquals("Fehlgeschlagen", steps[1].status)
            assertEquals("Ausstehend", steps[2].status); assertEquals(2, server.requestCount)
        }
    }
    @Test fun providerRejectionsHaveDistinctSafeMessagesAndCannotPassModelTest() = runBlocking {
        for (code in listOf("llm_invalid_key", "llm_forbidden", "llm_insufficient_quota", "llm_rate_limit", "llm_model_unavailable", "llm_server_error", "llm_unavailable", "llm_timeout")) MockWebServer().use { server ->
            server.enqueue(json("{\"ok\":true}")); server.enqueue(json("{}"))
            server.enqueue(json("{\"error\":{\"code\":\"$code\",\"message\":\"DO_NOT_DISPLAY_SECRET\"}}", 502))
            val steps = ConnectionDiagnostics(api(server)).run { }
            assertFalse(steps[3].status == "Erfolgreich")
            assertTrue(steps.any { it.detail == FailureMessages.message(code) })
            assertFalse(steps.toString().contains("DO_NOT_DISPLAY_SECRET"))
        }
    }
    @Test fun malformedOrNegativeResponseCannotPassConnectionTest() = runBlocking {
        for (body in listOf("not-json", "{\"ok\":false}", "{}", "null", "")) MockWebServer().use { server ->
            server.enqueue(json(body))
            val steps = ConnectionDiagnostics(api(server)).run { }
            assertEquals("Fehlgeschlagen", steps[0].status); assertEquals(1, server.requestCount)
        }
    }
    @Test fun negativeOrMalformedModelResponseNeverPasses() = runBlocking {
        for (body in listOf("{\"ok\":false}", "{}", "null", "not-json")) MockWebServer().use { server ->
            server.enqueue(json("{\"ok\":true}")); server.enqueue(json("{}")); server.enqueue(json(body))
            val steps = ConnectionDiagnostics(api(server)).run { }
            assertEquals("Erfolgreich", steps[0].status); assertEquals("Erfolgreich", steps[1].status)
            assertFalse(steps[3].status == "Erfolgreich")
            assertTrue("body=$body steps=$steps", steps[2].detail.contains("Ungültige"))
        }
    }
    @Test fun unreachableBackendIsReportedAtFirstStep() = runBlocking {
        val server = MockWebServer(); val api = api(server); server.close()
        val steps = ConnectionDiagnostics(api).run { }
        assertEquals("Fehlgeschlagen", steps[0].status)
        assertTrue(steps[0].detail.contains("Backend nicht erreichbar"))
    }
    @Test fun transportCausesAreSafeAndDistinct() {
        for ((error, code) in listOf(ConnectException("secret") to "backend_unreachable", UnknownHostException("secret") to "dns_error", SocketTimeoutException("secret") to "timeout", SSLHandshakeException("secret") to "tls_error")) {
            assertEquals(code, FailureMessages.code(error)); assertFalse(FailureMessages.message(code).contains("secret"))
        }
    }
}
