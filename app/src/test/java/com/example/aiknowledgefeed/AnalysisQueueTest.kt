package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class AnalysisQueueTest {
 @Test fun syntheticPdfWaitsForFreshKeyAndConsentThenRunsOnce() = runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val db=LocalDatabase.inMemory(context);val repo=LocalKnowledgeRepository(db)
  var calls=0
  val expected=JsonParser.parseString(javaClass.getResourceAsStream("/pdfs/expected.json")!!.bufferedReader().readText()).asJsonObject
  val model=object:ModelClient { override suspend fun complete(settings:Settings,instruction:String,data:JsonElement):JsonObject {
   calls++;delay(100)
   return JsonObject().apply { add("concepts",JsonArray().apply { expected.getAsJsonArray("concepts").forEach { e ->add(e.asJsonObject.deepCopy().apply {addProperty("description",get("name").asString+" aus dem synthetischen Dokument");add("connections",JsonArray().apply {if(e.asJsonObject.get("name").asString=="Transformer")add("Attention-Mechanismus")})}) } }) }
  }}
  try {
   val pdf=LocalPdfService(context,repo,model);val queue=AnalysisQueue(repo,pdf)
   val bytes=javaClass.getResourceAsStream("/pdfs/neural-transformer.pdf")!!.readBytes()
   val doc=pdf.importDocument(bytes,"neural-transformer.pdf")
   assertEquals(3,repo.dao.chunks(doc.id).map{it.page}.distinct().size)
   expected.getAsJsonArray("concepts").forEach { c -> assertTrue(repo.dao.chunks(doc.id).any { it.text.contains(c.asJsonObject.get("name").asString) }) }
   queue.reconcile(Settings());assertEquals("waiting_key",repo.dao.analysisTasks().single().state)
   val fresh=Settings(apiKey="fixture",model="fixture",directApiConsent=true,credentialEpoch=20261109)
   queue.reconcile(fresh);assertEquals("waiting_consent",repo.dao.analysisTasks().single().state);assertEquals(0,calls)
   queue.consent(doc.id);queue.reconcile(fresh)
   val results=listOf(async {queue.run(doc.id,fresh)},async {queue.run(doc.id,fresh)}).awaitAll()
   assertEquals(1,calls);assertEquals(6,repo.knowledge().size);assertTrue(repo.retrieve("Transformer").isEmpty())
   assertEquals("awaiting_confirmation",repo.dao.analysisTasks().single().state)
   queue.run(doc.id,fresh);assertEquals(1,calls)
   repo.knowledge().forEach { repo.updateKnowledge(it.id,KnowledgeUpdate(it.name,it.description,"confirmed")) }
   assertEquals("processed",repo.documents().single().status)
   assertTrue(repo.retrieve("Transformer").isNotEmpty())
   assertTrue(repo.knowledge().first{it.name=="Transformer"}.connections.contains(repo.knowledge().first{it.name=="Attention-Mechanismus"}.id))
   assertEquals(doc.id,pdf.importDocument(bytes,"duplicate.pdf").id)
  } finally {db.close()}
 }
 @Test fun failedAnalysisWaitsForExplicitRetryOrChangedConfiguration()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val db=LocalDatabase.inMemory(context);val repo=LocalKnowledgeRepository(db)
  var calls=0
  val model=object:ModelClient{override suspend fun complete(settings:Settings,instruction:String,data:JsonElement):JsonObject{calls++;throw ConnectionFailure("llm_invalid_key")}}
  try {
   val pdf=LocalPdfService(context,repo,model);val doc=pdf.importDocument(javaClass.getResourceAsStream("/pdfs/neural-transformer.pdf")!!.readBytes(),"synthetic.pdf")
   val queue=AnalysisQueue(repo,pdf);val settings=Settings(apiKey="fixture",model="fixture",credentialEpoch=20261109,directApiConsent=true)
   queue.consent(doc.id)
   try{queue.run(doc.id,settings);fail("Expected failure")}catch(e:ConnectionFailure){assertEquals("llm_invalid_key",e.code)}
   assertEquals("error",repo.dao.analysisTasks().single().state)
   assertTrue(repo.documents().single().message!!.isNotBlank())
   queue.run(doc.id,settings);assertEquals(1,calls)
   queue.consent(doc.id)
   try{queue.run(doc.id,settings)}catch(_:ConnectionFailure){}
   assertEquals(2,calls)
   queue.reconcile(settings.copy(model="new-fixture"));assertEquals("queued",repo.dao.analysisTasks().single().state)
  }finally{db.close()}
 }
 @Test fun removedStagedImportCannotBeRecreatedByLateExtraction()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val db=LocalDatabase.inMemory(context);val repo=LocalKnowledgeRepository(db)
  try {
   val model=object:ModelClient{override suspend fun complete(settings:Settings,instruction:String,data:JsonElement):JsonObject=error("No model needed")}
   try{LocalPdfService(context,repo,model).importDocument(javaClass.getResourceAsStream("/pdfs/neural-transformer.pdf")!!.readBytes(),"deleted.pdf",expectedDocumentId="deleted");fail("Deleted document recreated")}
   catch(e:ConnectionFailure){assertEquals("not_found",e.code)}
   assertTrue(repo.dao.documents().isEmpty())
  }finally{db.close()}
 }

}
