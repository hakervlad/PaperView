package com.example.aiknowledgefeed.backend.services

import com.example.aiknowledgefeed.backend.models.Settings
import java.net.URI

object ConnectionPolicy {
    fun validate(settings: Settings, debug: Boolean) {
        val uri = URI(settings.backendUrl)
        val local = debug && uri.scheme == "http" && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost")
        require((uri.scheme == "https" || local) && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null && !uri.host.isNullOrBlank()) { "Backend-Adresse muss HTTPS verwenden." }
        require(settings.token.isNotBlank()) { "Backend-Zugriffsschlüssel fehlt." }
        listOf(settings.token, settings.apiKey, settings.model, settings.llmBaseUrl).forEach { value -> require(value.none { it == '\r' || it == '\n' }) { "Ungültige Eingabe." } }
    }
}
