package com.example.aiknowledgefeed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.models.Paper
import com.example.aiknowledgefeed.backend.services.ConnectionFailure
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.time.LocalDate

/** Opt-in public HTTPS metadata only. No key, user profile or model call. */
@RunWith(AndroidJUnit4::class)
class PublicSourcesDeviceTest {
 private suspend fun probe(name:String,load:suspend(ScientificSources,LocalDate,LocalDate)->List<Paper>) {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("public_sources")=="true")
  val end=LocalDate.now()
  val papers=try{load(ScientificSources(),end.minusDays(30),end)}catch(e:ConnectionFailure){throw AssertionError("$name: ${e.code}")}
  Assert.assertTrue(papers.isNotEmpty());papers.forEach{Assert.assertTrue(it.url.startsWith("https://"));LocalDate.parse(it.published_at)}
  println("$name on Android: ${papers.size} real publications parsed over HTTPS")
 }
 @Test fun openAlexFromRealPhone()=runBlocking{probe("OpenAlex"){s,a,b->s.openAlex("artificial intelligence",a,b)}}
 @Test fun arxivFromRealPhone()=runBlocking{probe("arXiv"){s,a,b->s.arxiv("transformer",a,b)}}
}
