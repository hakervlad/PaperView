package com.example.aiknowledgefeed

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiknowledgefeed.backend.api.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.example.aiknowledgefeed.backend.services.*
import com.example.aiknowledgefeed.frontend.navigation.AppNavigation
import com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel
import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.*

/** Genuine Compose/Room flow, explicitly mocked paper source and model; zero paid requests. */
@RunWith(AndroidJUnit4::class)
class DeviceFeedWorkflowTest {
 @get:Rule val compose=createComposeRule()
 @Test fun understandButtonPersistsAndNextDayUsesSavedKnowledgeWhileSameDayRemainsCached() {
  val app=ApplicationProvider.getApplicationContext<Application>()
  require(BuildConfig.APPLICATION_ID.endsWith(".regression"))
  val path=java.io.File(app.noBackupFilesDir,"device-feed-${java.util.UUID.randomUUID()}.db")
  val db=LocalDatabase.create(app,path.absolutePath)
  var day=Instant.parse("2026-11-09T10:00:00Z");var calls=0;var searches=0
  val model=object:ModelClient{override suspend fun complete(settings:Settings,instruction:String,data:JsonElement):JsonObject {
   calls++;val known=data.asJsonObject.getAsJsonArray("confirmed_knowledge")
   return JsonObject().apply {
    addProperty("title","Attention im Test");addProperty("summary","Die synthetische Studie knüpft an dein bestätigtes Transformer-Wissen an.")
    add("concept_ids",JsonArray().apply{known.forEach{add(it.asJsonObject.get("id").asString)}})
    addProperty("new_insight","Synthetische Attention-Methode.");addProperty("connection","Dein Transformer-Wissen erklärt den Attention-Bezug.")
    addProperty("relevance","Bekannte Grundlagen anwenden.");addProperty("takeaway","Transformer und Attention im synthetischen Beispiel.")
   }
  }}
  val source=object:PaperSource{override suspend fun search(profile:Profile,known:List<Knowledge>,day:LocalDate):PaperSearchResult {
   searches++;return PaperSearchResult(listOf(Paper("fixture-$day","Transformer Studie $day",emptyList(),"Transformer Attention",day.toString(),"Wissenschaft","fixture","https://example.org/fixture-$day","unknown")))
  }}
  val repo=PaperViewRepository(app,db=db,model=model,source=source,now={day})
  runBlocking { SecureStore(app).settings(Settings());repo.initialize();repo.knowledge.saveProfile(Profile(bachelor_subject="Informatik",familiar_topics=listOf("Transformer"))) }
  val vm=PaperViewModel(app,repository=repo)
  try {
   compose.setContent{MaterialTheme{AppNavigation(false,{},vm=vm)}}
   compose.waitUntil(10000){!vm.state.value.busy}
   compose.onNodeWithText("Feed aktualisieren").performClick()
   compose.waitUntil(10000){vm.state.value.data.feed.items.size==1&&!vm.state.value.feedGenerating}
   compose.onNodeWithText("Attention im Test").performClick()
   compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Ich habe das Thema verstanden – zu meinem Wissen hinzufügen"))
   compose.onNodeWithText("Ich habe das Thema verstanden – zu meinem Wissen hinzufügen").performClick()
   compose.waitUntil(10000){vm.state.value.data.knowledge.any{it.source_paper_id=="fixture-2026-11-09"}}
   compose.onNodeWithText("In deinem Wissen gespeichert").assertIsDisplayed()
   compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("In meiner Wissenssammlung ansehen"))
   compose.onNodeWithText("In meiner Wissenssammlung ansehen").assertIsDisplayed().performClick()
   compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
   compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Transformer Studie 2026-11-09"))
   compose.onNodeWithText("Transformer Studie 2026-11-09").assertIsDisplayed()
   runBlocking {
    val saved=repo.knowledge.knowledge().first{it.source_paper_id!=null}
    val reopened=LocalDatabase.create(app,path.absolutePath)
    try{Assert.assertTrue(LocalKnowledgeRepository(reopened).knowledge().any{it.id==saved.id})}finally{reopened.close()}
    repo.generate(Settings(),"Europe/Berlin");Assert.assertEquals(1,calls);Assert.assertEquals(1,searches)
    day=day.plusSeconds(86400)
    val next=repo.generate(Settings(),"Europe/Berlin")
    Assert.assertTrue(next.items.single().prior_knowledge.any{it.id==saved.id})
    Assert.assertNotEquals("fixture-2026-11-09",next.items.single().paper_id)
    Assert.assertEquals(2,calls)
   }
  }finally {
   runBlocking{vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()};db.close();path.delete()
  }
 }
}
