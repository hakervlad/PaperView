package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalDatabaseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun profileAndConfirmedKnowledgeSurviveDatabaseReopenWithoutNetwork() = runBlocking {
        val path = File(context.noBackupFilesDir, "restart-test.db")
        path.delete()
        var db = LocalDatabase.create(context, path.absolutePath)
        var repo = LocalKnowledgeRepository(db)
        repo.saveProfile(Profile(bachelor_subject = "Informatik", familiar_topics = listOf("Bayes")))
        val known = repo.knowledge().single()
        assertEquals("confirmed", known.status)
        db.close()
        db = LocalDatabase.create(context, path.absolutePath); repo = LocalKnowledgeRepository(db)
        try {
            assertEquals("Informatik", repo.profile().bachelor_subject)
            assertEquals(known.id, repo.knowledge().single().id)
            assertEquals(known.id, repo.retrieve("Bayes Wahrscheinlichkeit").single().id)
            repo.saveProfile(repo.profile()); assertEquals(1, repo.knowledge().size)
            repo.deleteKnowledge(known.id)
            assertTrue(repo.knowledge().isEmpty()); assertTrue(repo.profile().familiar_topics.isEmpty())
        } finally { db.close(); path.delete() }
    }
    @Test fun candidatesNeverBecomePersonalizationContextWithoutConfirmation() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            val candidate = repo.addConcept("Transformer", "Neuronale Netze")
            assertTrue(repo.retrieve("Transformer Netze").isEmpty())
            repo.updateKnowledge(candidate.id, KnowledgeUpdate(candidate.name, candidate.description, "confirmed"))
            assertEquals(candidate.id, repo.retrieve("Transformer").single().id)
        } finally { db.close() }
    }
    @Test fun encryptedLegacySnapshotImportIsIdempotentAndDoesNotOverwriteLocalData() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            val original = repo.addConcept("Bayes", "Mein lokaler Text", "confirmed")
            val legacy = Snapshot(knowledge = listOf(Knowledge("old-id", "Bayes", "Alter Text", "confirmed")), profile = Profile(bachelor_subject = "Biologie"))
            repo.importSnapshot(legacy, "legacy-cache")
            repo.importSnapshot(legacy, "legacy-cache")
            assertEquals(original.id, repo.knowledge().single().id)
            assertEquals("Mein lokaler Text", repo.knowledge().single().description)
            assertEquals("Biologie", repo.profile().bachelor_subject)
        } finally { db.close() }
    }
}
