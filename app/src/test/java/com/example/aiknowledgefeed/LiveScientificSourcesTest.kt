package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.models.Paper
import com.example.aiknowledgefeed.backend.services.ConnectionFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

/** Independent public metadata probes; never a model call or personal knowledge upload. */
class LiveScientificSourcesTest {
    private suspend fun probe(name:String, load:suspend (ScientificSources,LocalDate,LocalDate)->List<Paper>) {
        assumeTrue("Explicit live source probe disabled",System.getenv("PAPERVIEW_LIVE_SOURCES")=="1")
        val end=LocalDate.now()
        val papers=try{load(ScientificSources(),end.minusDays(30),end)}catch(e:ConnectionFailure){throw AssertionError("$name: ${e.code}")}
        println("Live $name: successful HTTPS response, parsed papers=${papers.size}")
        papers.forEach{assertTrue(it.url.startsWith("https://"));LocalDate.parse(it.published_at)}
        if(name=="arXiv")papers.forEach{assertEquals("preprint",it.review_status)}
    }
    @Test fun shippingKotlinClientReachesOpenAlexOverHttps()=runBlocking {probe("OpenAlex"){s,a,b->s.openAlex("artificial intelligence",a,b)}}
    @Test fun shippingKotlinClientReachesArxivOverHttps()=runBlocking {probe("arXiv"){s,a,b->s.arxiv("transformer",a,b)}}
}
