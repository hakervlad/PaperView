package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.models.Settings
import com.example.aiknowledgefeed.backend.services.SettingsStorage
import com.example.aiknowledgefeed.backend.services.SettingsPersistence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SettingsPersistenceTest {
    private class Store(var value: Settings = Settings(), var fail: Boolean = false): SettingsStorage {
        override suspend fun settings() = value
        override suspend fun settings(value: Settings) { if (fail) throw java.io.IOException("fixture failure"); this.value = value }
    }
    @Test fun savesWithoutBackendOrNetworkAndReloads() = runBlocking {
        val store=Store();SettingsPersistence(store).save(Settings(apiKey="fixture-key"))
        assertEquals("fixture-key", SettingsPersistence(store).load().apiKey)
    }
    @Test fun emptyDraftPreservesStoredKeyAcrossRepositoryReinitialization() = runBlocking {
        val store=Store(Settings(apiKey="fixture-key"))
        SettingsPersistence(store).save(Settings(model="chosen-model"))
        val loaded=SettingsPersistence(store).load()
        assertEquals("fixture-key",loaded.apiKey);assertEquals("chosen-model",loaded.model)
    }
    @Test fun replacementIsExplicitlyPersisted() = runBlocking {
        val store=Store(Settings(apiKey="old-fixture"))
        assertEquals("new-fixture",SettingsPersistence(store).save(Settings(apiKey="new-fixture")).apiKey)
    }
    @Test fun explicitDeleteClearsKey() = runBlocking {
        val store=Store(Settings(apiKey="fixture-key"))
        SettingsPersistence(store).save(Settings(),clearApiKey=true)
        assertEquals("",store.value.apiKey)
    }
    @Test fun failureDoesNotEraseStoredCredentialsOrReportSuccess() = runBlocking {
        val store=Store(Settings(apiKey="old-fixture"),fail=true)
        try { SettingsPersistence(store).save(Settings(apiKey="new-fixture"));fail("Write must fail") }
        catch (_:java.io.IOException) { assertEquals("old-fixture",store.value.apiKey) }
    }
    @Test fun rotationUsesPersistedKeyInsteadOfPossiblyUnloadedUiState()=runBlocking {
        val store=Store(Settings(apiKey="old-fixture",credentialEpoch=0))
        val saved=SettingsPersistence(store).save(Settings(apiKey="old-fixture",credentialEpoch=20261109))
        assertEquals(0,saved.credentialEpoch)
        val fresh=SettingsPersistence(store).save(Settings(apiKey="fresh-fixture"))
        assertEquals(20261109,fresh.credentialEpoch)
        assertEquals(20261109,SettingsPersistence(store).save(Settings(model="changed")).credentialEpoch)
        assertEquals(0,SettingsPersistence(store).save(Settings(),clearApiKey=true).credentialEpoch)
    }

}
