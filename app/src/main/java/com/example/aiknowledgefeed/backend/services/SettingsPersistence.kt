package com.example.aiknowledgefeed.backend.services

import com.example.aiknowledgefeed.backend.models.Settings

interface SettingsStorage {
    suspend fun settings(): Settings
    suspend fun settings(value: Settings)
}

/** Local persistence only. Model/backend validation is an explicit separate action. */
class SettingsPersistence(private val storage: SettingsStorage) {
    companion object { const val KEY_EPOCH = 20261109 }
    suspend fun load(): Settings = storage.settings()
    suspend fun save(draft: Settings, clearApiKey: Boolean = false): Settings {
        val previous = storage.settings()
        val entered=draft.apiKey.trim()
        val epoch=when {
            clearApiKey -> 0
            entered.isBlank() || entered==previous.apiKey -> previous.credentialEpoch
            else -> KEY_EPOCH
        }
        val saved = draft.copy(credentialEpoch=epoch, apiKey = when {
            clearApiKey -> ""
            draft.apiKey.isBlank() -> previous.apiKey
            else -> draft.apiKey.trim()
        })
        storage.settings(saved)
        return saved
    }
}
