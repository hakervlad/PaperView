package com.example.aiknowledgefeed.frontend.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.PaperViewRepository
import com.example.aiknowledgefeed.backend.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

 data class AppState(val settings: Settings = Settings(), val data: Snapshot = Snapshot(), val busy: Boolean = true, val message: String? = null, val offline: Boolean = false, val extraPaper: Paper? = null, val settingsSaving: Boolean = false, val connectionSteps: List<ConnectionStep> = emptyList(), val feedGenerating: Boolean = false)

class PaperViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsPersistence: SettingsPersistence = SettingsPersistence(SecureStore(application)),
    private val repository: PaperViewRepository = PaperViewRepository(application),
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutable.asStateFlow()
    private var operationJob: Job? = null
    private var observation: Job? = null
    private var feedJob: Job? = null
    init {
        operationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var settingsWarning: String? = null
                val settings = try { settingsPersistence.load() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    Log.w("PaperView", "settings_load_failed type=${e.javaClass.simpleName}")
                    settingsWarning = "Sicher gespeicherte Einstellungen konnten nicht geladen werden. Lokales Wissen bleibt verfügbar; vorhandene Schlüssel werden nicht überschrieben."
                    Settings()
                }
                mutable.update { it.copy(settings = settings) }
                val warning = listOfNotNull(settingsWarning, repository.initialize()).joinToString(" ").ifBlank { null }
                val snapshot = repository.snapshot()
                mutable.update { it.copy(data = snapshot, message = warning) }
                observe()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { mutable.update { it.copy(busy = false) } }
            if (canGenerate()) generate()
        }
    }
    private fun observe() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch(Dispatchers.IO) {
            try { repository.observe().collect { snapshot -> mutable.update { it.copy(data = snapshot) } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
        }
    }
    private fun canGenerate(): Boolean {
        val s = mutable.value.settings; val p = mutable.value.data.profile
        return s.directApiConsent && s.apiKey.isNotBlank() && s.model.isNotBlank() &&
            (p.bachelor_subject.isNotBlank() || p.interests.isNotEmpty() || p.focus_areas.isNotEmpty() || mutable.value.data.knowledge.any { it.status == "confirmed" })
    }
    private fun fail(error: Exception) {
        // No exception text, request/response bodies, keys or URLs in logs.
        val code = FailureMessages.code(error)
        Log.w("PaperView", "operation_failed type=${error.javaClass.simpleName} reason=${FailureMessages.message(code)}")
        val offline = code in setOf("source_unavailable", "llm_unavailable", "llm_timeout", "network_error", "dns_error")
        mutable.update { it.copy(message = FailureMessages.message(code), offline = offline) }
    }
    private fun action(quietIfBusy: Boolean = false, block: suspend () -> Unit) {
        if (mutable.value.busy || mutable.value.settingsSaving) {
            if (!quietIfBusy) mutable.update { it.copy(message = "Ein Vorgang läuft bereits. Bitte nach Abschluss erneut versuchen.") }
            return
        }
        mutable.update { it.copy(busy = true, message = null) }
        operationJob = viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun refresh() = action(quietIfBusy = true) {
        val snapshot = repository.snapshot()
        mutable.update { it.copy(data = snapshot) }
        if (mutable.value.data.feed.date != LocalDate.now().toString() && canGenerate()) generate()
    }
    fun generate() {
        if (feedJob?.isActive == true || mutable.value.settingsSaving) return
        mutable.update { it.copy(feedGenerating = true, message = null) }
        feedJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val feed = repository.generate(mutable.value.settings, ZoneId.systemDefault().id)
                mutable.update { it.copy(data = it.data.copy(feed = feed), message = feed.message, offline = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { mutable.update { it.copy(feedGenerating = false) } }
        }
    }
    fun configure(settings: Settings, clearApiKey: Boolean = false): Job? {
        if (mutable.value.settingsSaving) return null
        mutable.update { it.copy(settingsSaving = true, message = null) }
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                operationJob?.cancelAndJoin(); feedJob?.cancelAndJoin()
                val saved = settingsPersistence.save(settings, clearApiKey)
                // Changing a model/key must never clear local knowledge or imported documents.
                mutable.update { it.copy(settings = saved, connectionSteps = emptyList(), message = if (saved.apiKey.isNotBlank())
                    "API-Key gespeichert. KI-Einstellungen lokal gespeichert."
                    else "KI-Einstellungen lokal gespeichert.") }
                try { repository.initialize(); observe() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.update { it.copy(message = "KI-Einstellungen sicher gespeichert. Die lokale Datenbank konnte noch nicht geladen werden.") } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.update { it.copy(message = "Sicheres Speichern fehlgeschlagen. Der bisher gespeicherte API-Key bleibt erhalten. Bitte erneut versuchen.") }
            } finally { mutable.update { it.copy(settingsSaving = false) } }
        }
    }
    fun test() = action {
        val steps = repository.test(mutable.value.settings) { progress -> mutable.update { it.copy(connectionSteps = progress) } }
        mutable.update { it.copy(message = if (steps.all { step -> step.status == "Erfolgreich" }) "Lokale Speicherung und echter Modellaufruf erfolgreich geprüft." else "Verbindungsprüfung nicht erfolgreich. Details bei den Prüfschritten.") }
    }
    fun profile(profile: Profile) = action {
        val saved = repository.knowledge.saveProfile(profile)
        mutable.update { it.copy(data = it.data.copy(profile = saved), message = "Wissensprofil dauerhaft auf diesem Gerät gespeichert.", offline = false) }
    }
    private fun List<Knowledge>.upsert(saved: Knowledge) = filterNot { it.id == saved.id } + saved
    fun openPaper(id: String) { if (mutable.value.data.feed.items.none { it.paper_id == id }) action { val paper = repository.knowledge.paper(id); mutable.update { it.copy(extraPaper = paper) } } }
    fun savePaper(id: String) = action {
        val saved = repository.knowledge.understood(id)
        mutable.update { it.copy(data = it.data.copy(knowledge = it.data.knowledge.upsert(saved)), message = "Thema dauerhaft auf diesem Gerät gespeichert.", offline = false) }
    }
    fun removeKnowledge(id: String) = action {
        repository.knowledge.deleteKnowledge(id)
        mutable.update { it.copy(data = it.data.copy(knowledge = it.data.knowledge.filterNot { k -> k.id == id }), message = "Wissenseintrag lokal entfernt.") }
    }
    fun updateKnowledge(value: Knowledge, name: String, description: String, status: String) = action {
        val saved = repository.knowledge.updateKnowledge(value.id, KnowledgeUpdate(name, description, status))
        mutable.update { it.copy(data = it.data.copy(knowledge = it.data.knowledge.upsert(saved)), message = "Wissenskonzept lokal gespeichert.") }
    }
    fun upload(uri: Uri) = action {
        val saved = repository.upload(uri)
        mutable.update { it.copy(data = it.data.copy(documents = it.data.documents.filterNot { d -> d.id == saved.id } + saved), message = "PDF lokal importiert und Text extrahiert. Noch keine externe KI-Verarbeitung.") }
    }
    fun analyze(id: String) = action {
        val saved = repository.analyze(id, mutable.value.settings)
        mutable.update { it.copy(data = it.data.copy(documents = it.data.documents.filterNot { d -> d.id == id } + saved, knowledge = saved.concepts.fold(it.data.knowledge) { values, k -> values.upsert(k) }), message = "Konzepte ermittelt. Bitte das mögliche Vorwissen prüfen und bestätigen.") }
    }
    fun removeDocument(id: String) = action {
        repository.knowledge.removeDocument(id)
        mutable.update { it.copy(data = it.data.copy(documents = it.data.documents.filterNot { d -> d.id == id }), message = "Dokument und Textabschnitte lokal gelöscht.") }
    }
    fun importArchive(uri: Uri) = action {
        val report = repository.importArchive(uri)
        val snapshot = repository.snapshot()
        mutable.update { it.copy(data = snapshot, message = if (report.alreadyImported) "Dieses Archiv wurde bereits übernommen." else "Archiv lokal übernommen: ${report.concepts} neue Konzepte, ${report.documents} Dokumente, ${report.feeds} Tagesausgaben.") }
    }
}
