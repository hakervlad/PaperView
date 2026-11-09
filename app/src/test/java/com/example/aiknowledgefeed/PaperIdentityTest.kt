package com.example.aiknowledgefeed
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class PaperIdentityTest {
 @Test fun distinctArxivPapersRemainDistinctAfterLocalStorage()=runBlocking {
  val xml="""<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>http://arxiv.org/abs/2611.08487v1</id><title>Fixture one</title><summary>Transformer fixture</summary><published>2026-11-08</published></entry><entry><id>http://arxiv.org/abs/2611.08443v1</id><title>Fixture two</title><summary>Attention fixture</summary><published>2026-11-08</published></entry></feed>"""
  val papers=ScientificSources.parseArxiv(xml)
  val db=LocalDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
  try {
   val repo=LocalKnowledgeRepository(db)
   Assert.assertNotEquals(papers[0].id,papers[1].id)
   Assert.assertNotEquals(repo.storePaper(papers[0]).id,repo.storePaper(papers[1]).id)
   Assert.assertEquals(2,repo.dao.papers().size)
  } finally {db.close()}
 }
}
