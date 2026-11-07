package com.example.aiknowledgefeed.frontend.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.frontend.viewmodels.*

@Composable
fun SettingsScreen(vm: PaperViewModel, state: AppState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var backend by remember(state.settings) { mutableStateOf(state.settings.backendUrl) }
    // Secrets deliberately not placed in rememberSaveable/Android saved-instance state.
    var token by remember(state.settings) { mutableStateOf(state.settings.token) }
    var provider by remember(state.settings) { mutableStateOf(state.settings.provider) }
    var model by remember(state.settings) { mutableStateOf(state.settings.model) }
    var base by remember(state.settings) { mutableStateOf(state.settings.llmBaseUrl) }
    var apiKeyDraft by remember { mutableStateOf("") }
    LaunchedEffect(state.settings) { apiKeyDraft = "" }
    val profile = state.data.profile
    var subject by remember(profile) { mutableStateOf(profile.bachelor_subject) }
    var focus by remember(profile) { mutableStateOf(profile.focus_areas.joinToString(", ")) }
    var interests by remember(profile) { mutableStateOf(profile.interests.joinToString(", ")) }
    var familiar by remember(profile) { mutableStateOf(profile.familiar_topics.joinToString(", ")) }
    var count by remember(profile) { mutableStateOf(profile.paper_count.toFloat()) }
    var days by remember(profile) { mutableStateOf(profile.lookback_days.toFloat()) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var filename by rememberSaveable { mutableStateOf("") }
    var consentDocument by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedUri = uri.toString()
            filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "Dokument.pdf" } ?: "Dokument.pdf"
        }
    }
    fun parts(text: String) = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineLarge)
        Text("Backend-Verbindung", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(backend, { backend = it }, label = { Text("Vertrauenswürdige Backend-URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(token, { token = it }, label = { Text("Backend-Zugriffsschlüssel") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Lokales HTTP ist ausschließlich im Debug-Build für localhost und den Emulator erlaubt. Eigene KI-Schlüssel benötigen immer HTTPS.")
        Text("KI-Modell konfigurieren", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(provider, { provider = it }, label = { Text("Anbieter (z. B. DeepSeek oder Server)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text("Modellname (leer: Server-Vorgabe)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(base, { base = it }, label = { Text("KI-Basis-URL (optional, serverseitig freizugeben)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKeyDraft, { apiKeyDraft = it }, label = { Text("Eigener API-Key (optional)") }, supportingText = { if (state.settings.apiKey.isNotBlank()) Text("API-Key gespeichert") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Modellaufrufe können Kosten verursachen. Der ausgewählte Anbieter erhält relevante Wissenseinträge und Paperinformationen. Dein Backend erhält den eigenen API-Key nur für Modellaufrufe.")
        Button(onClick = { vm.configure(Settings(backend.trim(), token.trim(), provider, model.trim(), base.trim(), apiKeyDraft.trim())) }, enabled = !state.settingsSaving) { Text("Verbindungseinstellungen speichern") }
        TextButton(onClick = { apiKeyDraft = ""; vm.configure(state.settings.copy(apiKey = ""), clearApiKey = true) }, enabled = !state.settingsSaving) { Text("Gespeicherten KI-Schlüssel löschen") }
        Button(onClick = vm::test, enabled = !state.busy) { Text("Gespeicherte Verbindung testen") }
        HorizontalDivider()
        Text("Persönliches Wissen", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(subject, { subject = it }, label = { Text("Studiengang / Bachelorfach") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(focus, { focus = it }, label = { Text("Schwerpunkte, mit Komma getrennt") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(interests, { interests = it }, label = { Text("Interessen, mit Komma getrennt") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(familiar, { familiar = it }, label = { Text("Ausdrücklich bekanntes Vorwissen") }, modifier = Modifier.fillMaxWidth())
        Text("Feed-Einstellungen", style = MaterialTheme.typography.titleLarge)
        Text("Gewünschte Paper: ${count.toInt()}")
        Slider(count, { count = it }, valueRange = 5f..10f, steps = 4)
        Text("Veröffentlichungen der letzten ${days.toInt()} Tage")
        Slider(days, { days = it }, valueRange = 7f..30f, steps = 22)
        Button(onClick = { vm.profile(Profile(subject, parts(focus), parts(interests), parts(familiar), count.toInt(), days.toInt())) }, enabled = !state.busy) { Text("Wissensprofil speichern") }
        HorizontalDivider()
        Text("Wissen importieren", style = MaterialTheme.typography.titleLarge)
        Text("Nur eigene oder berechtigt verwendete textbasierte PDFs (maximal 10 MB). Ein Upload überträgt die Datei an dein Backend, noch nicht an einen KI-Anbieter.")
        Button(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !state.busy) { Text("PDF auswählen") }
        if (selectedUri != null) {
            Text("Ausgewählt: $filename")
            Button(onClick = { vm.upload(android.net.Uri.parse(selectedUri)); selectedUri = null }, enabled = !state.busy) { Text("Upload und Textextraktion bestätigen") }
        }
        state.data.documents.forEach { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(document.filename, style = MaterialTheme.typography.titleMedium)
                    Text("Status: " + when (document.status) { "uploaded", "extracted" -> "Text extrahiert"; "analyzed", "completed" -> "Konzepte ermittelt"; "processing" -> "Verarbeitung läuft"; "failed", "error" -> "Verarbeitung fehlgeschlagen"; else -> document.status })
                    document.message?.let { Text(it) }
                    Button(onClick = { consentDocument = document.id }, enabled = !state.busy) { Text("Konzepte mit KI ermitteln") }
                    TextButton(onClick = { vm.removeDocument(document.id) }, enabled = !state.busy) { Text("Dokument und zugehörige Belege löschen") }
                }
            }
        }
        Text("Konzepte prüfen und bearbeiten", style = MaterialTheme.typography.titleLarge)
        Text("Aus Dokumenten abgeleitete Konzepte gelten erst nach deiner Bestätigung als Vorwissen.")
        state.data.knowledge.forEach { concept ->
            key(concept.id, concept.name, concept.description) {
                var name by remember { mutableStateOf(concept.name) }
                var description by remember { mutableStateOf(concept.description) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(when (concept.status) { "confirmed" -> "Bestätigtes Wissen"; "learning" -> "Möchte ich lernen"; else -> "Vorgeschlagenes Wissen" })
                    OutlinedTextField(name, { name = it }, label = { Text("Konzept") })
                    OutlinedTextField(description, { description = it }, label = { Text("Beschreibung") })
                    Button(onClick = { vm.updateKnowledge(concept, name, description, "confirmed") }, enabled = !state.busy && name.isNotBlank()) { Text("Als verstanden bestätigen") }
                    TextButton(onClick = { vm.updateKnowledge(concept, name, description, "learning") }, enabled = !state.busy) { Text("Möchte ich noch lernen") }
                    TextButton(onClick = { vm.removeKnowledge(concept.id) }, enabled = !state.busy) { Text("Wissenseintrag entfernen") }
                } }
            }
        }
    }
    consentDocument?.let { id ->
        AlertDialog(onDismissRequest = { consentDocument = null }, title = { Text("Externe KI-Verarbeitung erlauben?") },
            text = { Text("Benötigte Textabschnitte dieses Dokuments werden über dein Backend an den konfigurierten KI-Anbieter gesendet. Dies kann vertrauliche Inhalte übertragen und Kosten verursachen. Bestätige nur, wenn du dafür berechtigt bist.") },
            confirmButton = { TextButton(onClick = { consentDocument = null; vm.analyze(id) }) { Text("Zustimmen und analysieren") } },
            dismissButton = { TextButton(onClick = { consentDocument = null }) { Text("Abbrechen") } })
    }
}
