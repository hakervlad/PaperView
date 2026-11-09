package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.api.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.*

/** Requires separately approved budget and a freshly supplied protected file. Never reads old keys. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class PaidSystemTest {
 @Test fun explicitFiveCallBudgetChecksRealModelPdfAndTwoProfiles() = runBlocking {
  assumeTrue("Paid tests require explicit opt-in",System.getenv("PAPERVIEW_PAID_TEST_APPROVED")=="five-calls")
  val path=System.getenv("PAPERVIEW_FRESH_KEY_FILE")?:error("Fresh private key file required")
  val key=File(path).readText().trim();require(key.isNotBlank()&&key.all{it.code in 33..126})
  val settings=Settings(model="gpt-6-luna",apiKey=key,directApiConsent=true,credentialEpoch=20261109)
  val budget=File(System.getenv("PAPERVIEW_PAID_COUNTER_FILE")?:error("Persistent budget file required"))
  val model=object:ModelClient {
   override suspend fun complete(settings:Settings,instruction:String,data:JsonElement):JsonObject {
    val count=budget.readText().trim().toInt();check(count<5){"Approved budget exhausted"};budget.writeText((count+1).toString())
    println("Paid request ${count+1}/5 started")
    return DirectModelClient().complete(settings,instruction,data)
   }
  }
  assertTrue(model.complete(settings,"Gib exakt {\"ok\":true} zurueck.",JsonObject()).get("ok").asBoolean)
  println("LIVE connection: successful structured response")
  val context=ApplicationProvider.getApplicationContext<Context>();val db=LocalDatabase.inMemory(context);val second=LocalDatabase.inMemory(context)
  val repo=LocalKnowledgeRepository(db);val novice=LocalKnowledgeRepository(second)
  try {
   val pdf=LocalPdfService(context,repo,model)
   val document=pdf.importDocument(javaClass.getResourceAsStream("/pdfs/neural-transformer.pdf")!!.readBytes(),"synthetic-test.pdf")
   val analyzed=pdf.analyze(document.id,true,settings)
   check(analyzed.concepts.size>=4){"Insufficient extracted concepts"}
   println("LIVE PDF concepts: "+analyzed.concepts.joinToString{it.name})
   File(requireNotNull(System.getenv("PAPERVIEW_LIVE_RESULT_DIR")),"concepts.json").writeText(repo.gson.toJson(analyzed.concepts))
   analyzed.concepts.forEach { repo.updateKnowledge(it.id,KnowledgeUpdate(it.name,it.description,"confirmed")) }
   val sources=ScientificSources();val end=LocalDate.now();val candidates=sources.arxiv("attention transformer",end.minusDays(30),end)
   val paper=candidates.firstOrNull{it.abstract.isNotBlank()&&repo.retrieve(it.title+" "+it.abstract).isNotEmpty()}?:error("No current paper with verifiable prior context found")
   val expert=LocalPersonalizer(repo,model).explain(paper,settings)
   val beginner=LocalPersonalizer(novice,model).explain(paper,settings)
   check(expert.prior_knowledge.isNotEmpty()&&expert.connection.isNotBlank())
   check(beginner.prior_knowledge.isEmpty()&&beginner.connection.isBlank())
   check(expert.summary!=beginner.summary)
   val results=File(requireNotNull(System.getenv("PAPERVIEW_LIVE_RESULT_DIR")))
   File(results,"expert.json").writeText(repo.gson.toJson(expert));File(results,"beginner.json").writeText(repo.gson.toJson(beginner))
   // Persist the already paid explanation as day one, avoiding another paid generation.
   repo.storePaper(paper);ScientificSources.identifiers(paper).forEach { repo.dao.seen(com.example.aiknowledgefeed.backend.database.SeenRow(it)) };repo.dao.feed(com.example.aiknowledgefeed.backend.database.FeedRow(end.toString(),"success",repo.gson.toJson(Feed(end.toString(),"success",listOf(expert)))))
   val saved=repo.understood(paper.id)
   val following=candidates.firstOrNull { ScientificSources.identifiers(it).intersect(ScientificSources.identifiers(paper)).isEmpty() && repo.retrieve(it.title+" "+it.abstract).any { k -> k.id==saved.id } } ?: error("No second real paper with matching new knowledge; no fabricated fallback")
   val source=object:PaperSource{override suspend fun search(profile:Profile,known:List<Knowledge>,day:LocalDate)=PaperSearchResult(listOf(following))}
   val day2=LocalFeedService(repo,source,LocalPersonalizer(repo,model)){Instant.now().plusSeconds(86400)}.generate(settings,"Europe/Berlin")
   check(day2.items.isNotEmpty()&&day2.items.all{it.paper_id!=paper.id})
   check(day2.items.any{item->item.prior_knowledge.any{it.id==saved.id}})
   File(results,"day2.json").writeText(repo.gson.toJson(day2))
   println("LIVE summaries validated. New knowledge in day-two retrieval: "+repo.retrieve(following.title+" "+following.abstract).any{it.id==saved.id})
   println("Real source: "+paper.url)
  } finally{db.close();second.close()}
 }
}
