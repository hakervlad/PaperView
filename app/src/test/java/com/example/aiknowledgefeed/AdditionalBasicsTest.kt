package com.example.aiknowledgefeed
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class AdditionalBasicsTest {
 @Test fun missingFoundationsAreDisplayedWithoutInventingPriorKnowledge()= runBlocking {
  val db=LocalDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
  try {
   val model=object:ModelClient{override suspend fun complete(settings:Settings,instruction:String,data:JsonElement)=JsonParser.parseString("""{"title":"Methode","summary":"Eine neue Methode.","concept_ids":[],"new_insight":"Aus dem Abstract.","connection":"","relevance":"Einordnung","takeaway":"Eine Methode.","additional_basics":["Ein Embedding ist eine gelernte Zahlenrepräsentation."]}""").asJsonObject}
   val paper=Paper("p","Methode",emptyList(),"Abstract","2026-11-09","Wissenschaft","fixture","https://example.org/p","unknown")
   val result=LocalPersonalizer(LocalKnowledgeRepository(db),model).explain(paper,Settings())
   Assert.assertTrue(result.prior_knowledge.isEmpty());Assert.assertTrue(result.asArticle().detail.contains("Zusätzliche Grundlagen"));Assert.assertTrue(result.asArticle().detail.contains("Zahlenrepräsentation"))
  }finally{db.close()}
 }
}
