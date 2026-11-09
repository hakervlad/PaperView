package com.example.aiknowledgefeed

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.example.aiknowledgefeed.frontend.navigation.AppNavigation
import com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import com.google.gson.*

@RunWith(AndroidJUnit4::class)
class SyntheticImportDeviceTest {
 @get:Rule val compose=createComposeRule()
 @Test fun realSafPickerImportsPdfAndPersistentWorkerExtractsWithoutKey() {
  val app=ApplicationProvider.getApplicationContext<Application>()
  require(BuildConfig.APPLICATION_ID.endsWith(".regression"))
  val repo=PaperViewRepository(app)
  runBlocking { SecureStore(app).settings(Settings()) }
  val vm=PaperViewModel(app)
  compose.setContent{MaterialTheme{AppNavigation(false,{},vm=vm)}}
  compose.waitUntil(15000){!vm.state.value.busy}
  compose.onNodeWithText("Einstellungen").performClick()
  compose.onNodeWithText("PDF auswählen").performScrollTo().performClick()
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  val selector=UiSelector().text("PaperView-synthetic.pdf")
  if(!device.findObject(selector).waitForExists(10000)) {
   val roots=device.findObject(UiSelector().descriptionMatches("(?i).*(Wurzeln anzeigen|Show roots|Open navigation drawer).*"))
   if(roots.waitForExists(3000)) roots.click() else {
    val toolbar=device.findObject(UiSelector().resourceIdMatches(".*:id/toolbar"))
    if(toolbar.waitForExists(3000)) toolbar.getChild(UiSelector().className("android.widget.ImageButton")).click()
   }
   val downloads=device.findObject(UiSelector().textMatches("(?i)Downloads?"))
   Assert.assertTrue("Downloads must be available; current package="+device.currentPackageName,downloads.waitForExists(5000));downloads.click()
  }
  // UiObject resolves the selector at click time; drawer transitions invalidate UiObject2.
  Assert.assertTrue("Synthetic PDF must be selectable",device.findObject(selector).waitForExists(10000))
  device.findObject(selector).click()
  compose.onNodeWithText("Lokal importieren und Text extrahieren").performScrollTo().performClick()
  compose.waitUntil(30000){vm.state.value.data.documents.any{it.filename=="PaperView-synthetic.pdf"&&it.status=="waiting_key"}}
  val document=vm.state.value.data.documents.first{it.filename=="PaperView-synthetic.pdf"}
  compose.onNodeWithText("PaperView-synthetic.pdf").performScrollTo().assertIsDisplayed()
  runBlocking {
   Assert.assertEquals(3,repo.knowledge.dao.chunks(document.id).map{it.page}.distinct().size)
   Assert.assertTrue(File(app.noBackupFilesDir,"pdfs/${document.id}.pdf").exists())
   Assert.assertTrue(repo.knowledge.knowledge().isEmpty())
   Assert.assertFalse(repo.knowledge.dao.analysisTasks().single{it.documentId==document.id}.consent)
  }
  // The device analysis below is explicitly mocked; no real key enters this app.
  runBlocking {
   val expected=JsonParser.parseString(InstrumentationRegistry.getInstrumentation().context.assets.open("pdfs/expected.json").bufferedReader().readText()).asJsonObject
   val model=object:ModelClient {override suspend fun complete(settings:Settings,instruction:String,data:JsonElement):JsonObject {
    return JsonObject().apply{add("concepts",JsonArray().apply{expected.getAsJsonArray("concepts").forEach{e->add(e.asJsonObject.deepCopy().apply{addProperty("description","Begriff aus dem synthetischen Dokument");add("connections",JsonArray().apply{if(e.asJsonObject.get("name").asString=="Transformer")add("Attention-Mechanismus")})})}})}
   }}
   val queue=AnalysisQueue(repo.knowledge,LocalPdfService(app,repo.knowledge,model))
   queue.consent(document.id);queue.run(document.id,Settings(apiKey="fixture",model="fixture",directApiConsent=true,credentialEpoch=20261109))
  }
  compose.waitUntil(15000){vm.state.value.data.knowledge.size==6}
  repeat(6){index ->
   compose.onAllNodesWithText("Als verstanden bestätigen")[index].performScrollTo().performClick()
   compose.waitUntil(10000){!vm.state.value.busy}
  }
  compose.waitUntil(15000){vm.state.value.data.knowledge.count{it.status=="confirmed"}==6}
  compose.onNodeWithText("Mein Wissen").performClick()
  compose.onNodeWithText("Machine Learning").assertExists()
  val recreated=PaperViewModel(app)
  compose.waitUntil(15000){recreated.state.value.data.documents.any{it.id==document.id&&it.status=="processed"}}
 }
}
