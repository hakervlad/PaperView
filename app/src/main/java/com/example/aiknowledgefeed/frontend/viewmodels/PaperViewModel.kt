package com.example.aiknowledgefeed.frontend.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.PaperViewRepository
import com.example.aiknowledgefeed.backend.services.SecureStore
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.time.LocalDate
import java.time.ZoneId

 data class AppState(val settings: Settings = Settings(), val data: Snapshot = Snapshot(), val busy: Boolean = false, val message: String? = null, val offline: Boolean = false, val extraPaper: Paper? = null)
class PaperViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PaperViewRepository(SecureStore(application))
    private val mutable = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutable.asStateFlow()
    private var attemptedDay = ""
    private var lastSyncAttempt = 0L
    init { action {
        val settings = repository.store.settings()
        val cache = repository.store.snapshot()
        mutable.update { it.copy(settings = settings, data = cache) }
        if (settings.token.isNotBlank()) sync(true)
        else mutable.update { it.copy(message = "Bitte zuerst das Backend in den Einstellungen konfigurieren.") }
    } }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = safeError(e), offline = e is java.io.IOException) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun safeError(e: Exception): String = when (e) {
        is HttpException -> runCatching { JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject.getAsJsonObject("error").get("message").asString }.getOrNull()
            ?: "Backend-Anfrage fehlgeschlagen (${e.code()}). Bitte Einstellungen prüfen."
        is java.io.IOException -> "Verbindung nicht verfügbar. Der zuletzt gespeicherte Stand bleibt sichtbar."
        is IllegalArgumentException, is IllegalStateException -> "Ungültige Eingabe oder unsichere Verbindung. Bitte HTTPS-Adresse und Zugriffsschlüssel prüfen."
        else -> "Verarbeitung fehlgeschlagen. Bitte erneut versuchen."
    }
    private fun api() = repository.api(mutable.value.settings)
    private fun persist(data: Snapshot) { repository.store.snapshot(data); mutable.update { it.copy(data = data, offline = false) } }
    private suspend fun sync(generate: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (generate && now - lastSyncAttempt < 60_000 && lastSyncAttempt != 0L) return
        lastSyncAttempt = now
        val api = api(); val timezone = ZoneId.systemDefault().id
        val feed = api.feed(timezone)
        persist(mutable.value.data.copy(feed = feed, profile = api.profile(), knowledge = api.knowledge(), documents = api.documents()))
        val today = LocalDate.now().toString()
        if (generate && attemptedDay != today && (feed.date != today || feed.status !in listOf("completed", "success", "ready"))) {
            attemptedDay = today
            val next = api.generate(Generation(timezone))
            persist(mutable.value.data.copy(feed = next))
            mutable.update { it.copy(message = next.message) }
        }
    }
    fun refresh() = action { sync(true) }
    fun generate() = action {
        val feed = api().generate(Generation(ZoneId.systemDefault().id))
        persist(mutable.value.data.copy(feed = feed)); mutable.update { it.copy(message = feed.message) }
    }
    fun configure(settings: Settings) = action {
        repository.api(settings) // Validate before persisting. No network request.
        if (settings.backendUrl != mutable.value.settings.backendUrl || settings.token != mutable.value.settings.token) {
            repository.store.clearCache(); mutable.update { it.copy(data = Snapshot()) }; attemptedDay = ""
        }
        repository.store.settings(settings); mutable.update { it.copy(settings = settings, message = "Verbindungseinstellungen gespeichert.") }; sync(false)
    }
    fun test() = action { val result = api().test(); mutable.update { it.copy(message = result.message) } }
    fun profile(profile: Profile) = action { val saved = api().profile(profile); persist(mutable.value.data.copy(profile = saved, knowledge = api().knowledge())) }
    fun openPaper(id: String) { if (mutable.value.data.feed.items.none { it.paper_id == id }) action { val paper = api().paper(id); mutable.update { it.copy(extraPaper = paper) } } }
    fun savePaper(id: String) = action { api().understood(id); persist(mutable.value.data.copy(knowledge = api().knowledge())) }
    fun removeKnowledge(id: String) = action { api().deleteKnowledge(id); persist(mutable.value.data.copy(knowledge = api().knowledge())) }
    fun updateKnowledge(value: Knowledge, name: String, description: String, status: String) = action {
        api().update(value.id, KnowledgeUpdate(name, description, status)); persist(mutable.value.data.copy(knowledge = api().knowledge(), documents = api().documents()))
    }
    fun upload(uri: Uri) = action { repository.upload(api(), getApplication(), uri); persist(mutable.value.data.copy(documents = api().documents())) }
    fun analyze(id: String) = action { api().analyze(id, Consent(true)); persist(mutable.value.data.copy(documents = api().documents(), knowledge = api().knowledge())) }
    fun removeDocument(id: String) = action { api().deleteDocument(id); persist(mutable.value.data.copy(documents = api().documents(), knowledge = api().knowledge())) }
}
