package com.example.aiknowledgefeed
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiknowledgefeed.backend.repositories.PaperViewRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class DevicePersistenceCheckTest {
 @Test fun savedKnowledgeAndOriginalPdfSurviveExternalProcessRestart()=runBlocking {
  Assume.assumeTrue("Requires explicit prior system-test phase",androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("verify_restart")=="true")
  val context=ApplicationProvider.getApplicationContext<Context>();val repo=PaperViewRepository(context)
  val doc=repo.knowledge.documents().first{it.filename=="PaperView-synthetic.pdf"}
  Assert.assertEquals("processed",doc.status)
  Assert.assertEquals(6,repo.knowledge.knowledge().count{it.status=="confirmed"})
  Assert.assertEquals(3,repo.knowledge.dao.chunks(doc.id).map{it.page}.distinct().size)
  Assert.assertTrue(File(context.noBackupFilesDir,"pdfs/${doc.id}.pdf").exists())
 }
}
