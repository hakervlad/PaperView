package com.example.aiknowledgefeed.backend.services

import com.example.aiknowledgefeed.backend.models.Settings

interface SettingsStorage {
    suspend fun settings(): Settings
    suspend fun settings(value: Settings)
}

/** Local persistence only. Model/backend validation is an explicit separate action. */
class SettingsPersistence(private val storage: SettingsStorage) {
    suspend fun load(): Settings = storage.settings()
    suspend fun save(draft: Settings, clearApiKey: Boolean = false): Settings {
        val previous = storage.settings()
        val saved = draft.copy(apiKey = when {
            clearApiKey -> ""
            draft.apiKey.isBlank() -> previous.apiKey
            else -> draft.apiKey.trim()
        })
        storage.settings(saved)
        return saved
    }
}
