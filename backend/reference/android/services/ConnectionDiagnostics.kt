package com.example.aiknowledgefeed.backend.services

import com.example.aiknowledgefeed.backend.api.PaperViewApi
import com.example.aiknowledgefeed.backend.models.ConnectionResult
import com.example.aiknowledgefeed.backend.models.Health
import com.example.aiknowledgefeed.backend.models.Profile
import kotlinx.coroutines.CancellationException

data class ConnectionStep(val title: String, val status: String = "Ausstehend", val detail: String = "")

class ConnectionDiagnostics(
    private val api: PaperViewApi,
    private val onFailure: (Exception) -> Unit = {},
) {
    suspend fun run(progress: (List<ConnectionStep>) -> Unit): List<ConnectionStep> {
        val steps = mutableListOf(
            ConnectionStep("Backend erreichbar"), ConnectionStep("Backend-Antwort und Zugriff"),
            ConnectionStep("Modellanbieter erreichbar"), ConnectionStep("Echter Modellaufruf"),
        )
        fun report(index: Int, status: String, detail: String = "") {
            steps[index] = steps[index].copy(status = status, detail = detail)
            progress(steps.toList())
        }
        var stage = 0
        try {
            progress(steps.toList())
            report(0, "Wird geprüft")
            val health: Health? = api.health()
            if (health?.ok != true) throw ConnectionFailure("invalid_response")
            report(0, "Erfolgreich")
            stage = 1
            report(1, "Wird geprüft")
            val profile: Profile? = api.profile()
            if (profile == null) throw ConnectionFailure("invalid_response")
            report(1, "Erfolgreich")
            stage = 2
            report(2, "Wird geprüft")
            report(3, "Wird geprüft")
            val result: ConnectionResult? = api.test()
            if (result?.ok != true) throw ConnectionFailure("invalid_response")
            report(2, "Erfolgreich")
            report(3, "Erfolgreich", "Modell hat die angeforderte Antwort geliefert.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = FailureMessages.code(e)
            onFailure(e)
            // These codes require an actual HTTP response from the provider.
            val providerAnswered = code in setOf(
                "llm_invalid_key", "llm_forbidden", "llm_insufficient_quota", "llm_rate_limit",
                "llm_model_unavailable", "llm_configuration", "llm_invalid_json", "llm_server_error",
            )
            if (stage == 2 && providerAnswered) {
                report(2, "Erfolgreich", "Anbieter hat geantwortet.")
                report(3, "Fehlgeschlagen", FailureMessages.message(code))
            } else {
                report(stage, "Fehlgeschlagen", FailureMessages.message(code))
                if (stage == 2) report(3, "Nicht bestätigt", "Kein erfolgreicher Modellaufruf.")
            }
        }
        return steps.toList()
    }
}
