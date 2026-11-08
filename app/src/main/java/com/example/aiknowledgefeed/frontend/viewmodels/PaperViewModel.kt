package com.example.aiknowledgefeed.frontend.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.PaperViewRepository
import com.example.aiknowledgefeed.backend.services.SettingsPersistence
import com.example.aiknowledgefeed.backend.services.SecureStore
import com.example.aiknowledgefeed.backend.services.ConnectionStep
import com.example.aiknowledgefeed.backend.services.ConnectionDiagnostics
import com.example.aiknowledgefeed.backend.services.FailureMessages
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.time.LocalDate
import java.time.ZoneId

 data class AppState(val settings: Settings = Settings(), val data: Snapshot = Snapshot(), val busy: Boolean = false, val message: String? = null, val offline: Boolean = false, val extraPaper: Paper? = null, val settingsSaving: Boolean = false, val connectionSteps: List<ConnectionStep> = emptyList())
class PaperViewModel @JvmOverloads constructor(application: Application,
    private val settingsPersistence: SettingsPersistence = SettingsPersistence(SecureStore(application)),
) : AndroidViewModel(application) {
    private val repository = PaperViewRepository(SecureStore(application))
    private val mutable = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutable.asStateFlow()
    private var operationJob: Job? = null
    private var attemptedDay = ""
    private var lastSyncAttempt = 0L
    init { action {
        val settings = try { settingsPersistence.load() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            mutable.update { it.copy(message = "Gespeicherte Einstellungen konnten nicht sicher geladen werden. Bitte erneut versuchen.") }
            return@action
        }
        mutable.update { it.copy(settings = settings) }
        val cache = try { repository.store.snapshot() } catch (_: Exception) {
            mutable.update { it.copy(message = "Der Offline-Cache konnte nicht geladen werden. Daten werden erneut vom Backend abgerufen.") }
            Snapshot()
        }
        mutable.update { it.copy(settings = settings, data = cache) }
        if (settings.token.isNotBlank()) sync(true)
        else mutable.update { it.copy(message = "Bitte zuerst das Backend in den Einstellungen konfigurieren.") }
    } }
    private fun action(quietIfBusy: Boolean = false, block: suspend () -> Unit) {
        if (mutable.value.busy || mutable.value.settingsSaving) {
            if (!quietIfBusy) mutable.update { it.copy(message = "Ein Vorgang läuft bereits. Bitte nach Abschluss erneut versuchen; es wurde nichts zusätzlich gespeichert.") }
            return
        }
        mutable.update { it.copy(busy = true, message = null) }
        operationJob = viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = safeError(e), offline = e is java.io.IOException) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun safeError(e: Exception): String {
        // Never log Throwable/message, URLs, headers, documents or response bodies.
        Log.w("PaperView", "request_failed type=${e.javaClass.simpleName} status=${(e as? HttpException)?.code() ?: 0}")
        return FailureMessages.message(FailureMessages.code(e))
    }
    private fun api() = repository.api(mutable.value.settings)
    private fun persist(data: Snapshot): Boolean {
        // A server acknowledgement remains valid even if the optional local cache fails.
        mutable.update { it.copy(data = data, offline = false) }
        return try { repository.store.snapshot(data); true }
        catch (_: Exception) {
            mutable.update { it.copy(message = "Serverdaten geladen. Der verschlüsselte Offline-Cache konnte nicht aktualisiert werden.") }; false
        }
    }
    private suspend fun acknowledged(data: Snapshot, confirmation: String, refresh: (suspend () -> Snapshot)? = null) {
        val cached = persist(data)
        mutable.update { it.copy(message = confirmation + if (cached) "" else " Der Offline-Cache konnte nicht aktualisiert werden.") }
        if (refresh != null) try {
            if (!persist(refresh())) mutable.update { it.copy(message = "$confirmation Der Offline-Cache konnte nicht aktualisiert werden.") }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.update { it.copy(message = "$confirmation Weitere Daten konnten nicht aktualisiert werden: ${safeError(e)}") }
        }
    }
    private fun List<Knowledge>.upsert(value: Knowledge) = filterNot { it.id == value.id } + value
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
    fun refresh() = action(quietIfBusy = true) { sync(true) }
    fun generate() = action {
        val feed = api().generate(Generation(ZoneId.systemDefault().id))
        persist(mutable.value.data.copy(feed = feed)); mutable.update { it.copy(message = feed.message) }
    }
    fun configure(settings: Settings, clearApiKey: Boolean = false): Job? {
        if (mutable.value.settingsSaving) return null
        mutable.update { it.copy(settingsSaving = true, message = null) }
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                // Do not discard a save because a refresh is in flight, or let an old
                // request repopulate cache after the backend identity has changed.
                operationJob?.cancelAndJoin()
                val previous = mutable.value.settings
                val saved = settingsPersistence.save(settings, clearApiKey)
                mutable.update { it.copy(settings = saved, connectionSteps = emptyList(), message = if (saved.apiKey.isNotBlank())
                    "API-Key gespeichert. Verbindungseinstellungen lokal gespeichert."
                    else "Verbindungseinstellungen lokal gespeichert.") }
                if (saved.backendUrl != previous.backendUrl || saved.token != previous.token) {
                    attemptedDay = ""
                    lastSyncAttempt = 0L
                    mutable.update { it.copy(data = Snapshot()) }
                    try { repository.store.clearCache() } catch (_: Exception) {
                        mutable.update { it.copy(message = "Einstellungen gespeichert. Der alte Offline-Cache konnte nicht gelöscht werden.") }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.update { it.copy(message = "Sicheres Speichern fehlgeschlagen. Der bisher gespeicherte API-Key bleibt erhalten. Bitte erneut versuchen.") }
            } finally { mutable.update { it.copy(settingsSaving = false) } }
        }
    }
    fun test() = action {
        // Configuration and transport failures are separate from the locally saved key.
        val steps = ConnectionDiagnostics(api()) { error ->
            Log.w("PaperView", "connection_check_failed type=${error.javaClass.simpleName} status=${(error as? HttpException)?.code() ?: 0}")
        }.run { progress -> mutable.update { it.copy(connectionSteps = progress) } }
        mutable.update { it.copy(message = if (steps.all { step -> step.status == "Erfolgreich" })
            "Backend und echter Modellaufruf erfolgreich geprüft."
            else "Verbindungsprüfung nicht erfolgreich. Details bei den Prüfschritten.") }
    }
    fun profile(profile: Profile) = action {
        val saved = api().profile(profile)
        acknowledged(mutable.value.data.copy(profile = saved), "Wissensprofil auf dem Backend gespeichert.") {
            mutable.value.data.copy(knowledge = api().knowledge())
        }
    }
    fun openPaper(id: String) { if (mutable.value.data.feed.items.none { it.paper_id == id }) action { val paper = api().paper(id); mutable.update { it.copy(extraPaper = paper) } } }
    fun savePaper(id: String) = action {
        val saved = api().understood(id)
        acknowledged(mutable.value.data.copy(knowledge = mutable.value.data.knowledge.upsert(saved)), "Thema dauerhaft auf dem Backend gespeichert.")
    }
    fun removeKnowledge(id: String) = action {
        api().deleteKnowledge(id)
        val knowledge = mutable.value.data.knowledge.filterNot { it.id == id }.map { it.copy(connections = it.connections - id) }
        acknowledged(mutable.value.data.copy(knowledge = knowledge), "Wissenseintrag auf dem Backend entfernt.") {
            mutable.value.data.copy(knowledge = api().knowledge(), profile = api().profile(), documents = api().documents())
        }
    }
    fun updateKnowledge(value: Knowledge, name: String, description: String, status: String) = action {
        val saved = api().update(value.id, KnowledgeUpdate(name, description, status))
        acknowledged(mutable.value.data.copy(knowledge = mutable.value.data.knowledge.upsert(saved)), "Wissenskonzept auf dem Backend gespeichert.") {
            mutable.value.data.copy(documents = api().documents(), profile = api().profile())
        }
    }
    fun upload(uri: Uri) = action {
        val saved = repository.upload(api(), getApplication(), uri)
        acknowledged(mutable.value.data.copy(documents = mutable.value.data.documents.filterNot { it.id == saved.id } + saved), "PDF auf dem Backend gespeichert und Text extrahiert.")
    }
    fun analyze(id: String) = action {
        val saved = api().analyze(id, Consent(true))
        val knowledge = saved.concepts.fold(mutable.value.data.knowledge) { values, concept -> values.upsert(concept) }
        acknowledged(mutable.value.data.copy(documents = mutable.value.data.documents.filterNot { it.id == id } + saved, knowledge = knowledge), "Konzepte ermittelt. Bitte das mögliche Vorwissen prüfen und bestätigen.")
    }
    fun removeDocument(id: String) = action {
        api().deleteDocument(id)
        acknowledged(mutable.value.data.copy(documents = mutable.value.data.documents.filterNot { it.id == id }), "Dokument auf dem Backend gelöscht.") {
            mutable.value.data.copy(knowledge = api().knowledge())
        }
    }
}
