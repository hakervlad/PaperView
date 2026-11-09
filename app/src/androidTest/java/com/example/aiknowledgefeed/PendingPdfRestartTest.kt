package com.example.aiknowledgefeed

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.example.aiknowledgefeed.backend.database.*
import com.example.aiknowledgefeed.backend.repositories.PaperViewRepository
import com.example.aiknowledgefeed.backend.services.PdfExtractionWorker
import com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Two explicit invocations separated by an external force-stop; no live model or credentials. */
@RunWith(AndroidJUnit4::class)
class PendingPdfRestartTest {
 private val app:Application get()=ApplicationProvider.getApplicationContext()
 private val id="pending-synthetic-restart"
 @Test fun stagePersistentExtractionBeforeExternalProcessDeath()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("pending_phase")=="stage")
  require(BuildConfig.APPLICATION_ID.endsWith(".regression"))
  val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("pdfs/neural-transformer.pdf").use{it.readBytes()}+"\n% pending restart fixture\n".toByteArray()
  val file=File(app.noBackupFilesDir,"pdfs/$id.pdf");file.parentFile!!.mkdirs();file.writeBytes(bytes)
  val repo=PaperViewRepository(app)
  repo.knowledge.dao.document(DocumentRow(id,"Pending-worker-synthetic.pdf","imported",null,java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}))
  repo.knowledge.dao.analysisTask(AnalysisTaskRow(id,System.currentTimeMillis(),state="imported"));repo.knowledge.changed()
  val work=OneTimeWorkRequestBuilder<PdfExtractionWorker>().setInputData(workDataOf("document_id" to id)).setInitialDelay(15,TimeUnit.SECONDS).build()
  WorkManager.getInstance(app).enqueueUniqueWork("pdf-extraction:$id",ExistingWorkPolicy.KEEP,work).result.get()
  Assert.assertEquals("imported",repo.knowledge.dao.documents().first{it.id==id}.status)
 }
 @Test fun reopenedAppCompletesPreviouslyPendingLocalExtraction()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("pending_phase")=="verify")
  require(BuildConfig.APPLICATION_ID.endsWith(".regression"))
  val vm=PaperViewModel(app);val repo=PaperViewRepository(app)
  withTimeout(45000){while(repo.knowledge.dao.chunks(id).isEmpty())delay(100)}
  Assert.assertEquals(3,repo.knowledge.dao.chunks(id).map{it.page}.distinct().size)
  Assert.assertTrue(File(app.noBackupFilesDir,"pdfs/$id.pdf").exists())
  Assert.assertFalse(repo.knowledge.dao.analysisTasks().first{it.documentId==id}.consent)
 }
}
