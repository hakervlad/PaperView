package com.example.aiknowledgefeed.backend.services

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.util.Base64
import android.util.AtomicFile
import com.google.gson.Gson
import com.example.aiknowledgefeed.backend.models.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsDataStore by preferencesDataStore("connection")
class SecureStore(private val context: Context) {
    private val gson = Gson()
    private val settingKey = stringPreferencesKey("encrypted_settings")
    private val cache = AtomicFile(File(context.noBackupFilesDir, "feed-cache.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("paperview-storage", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("paperview-storage", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(text: String): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key()); iv + doFinal(text.toByteArray(Charsets.UTF_8))
    }
    private fun decrypt(bytes: ByteArray): String = Cipher.getInstance("AES/GCM/NoPadding").run {
        require(bytes.size > 28); init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    suspend fun settings(): Settings {
        val raw = context.settingsDataStore.data.first()[settingKey] ?: return Settings()
        return gson.fromJson(decrypt(Base64.decode(raw, Base64.NO_WRAP)), Settings::class.java)
    }
    suspend fun settings(value: Settings) { context.settingsDataStore.edit { it[settingKey] = Base64.encodeToString(encrypt(gson.toJson(value)), Base64.NO_WRAP) } }
    fun snapshot(): Snapshot = if (cache.baseFile.exists()) gson.fromJson(decrypt(cache.readFully()), Snapshot::class.java) else Snapshot()
    fun snapshot(value: Snapshot) {
        val stream = cache.startWrite()
        try { stream.write(encrypt(gson.toJson(value))); cache.finishWrite(stream) } catch (e: Exception) { cache.failWrite(stream); throw e }
    }
    fun clearCache() { cache.delete() }
}
