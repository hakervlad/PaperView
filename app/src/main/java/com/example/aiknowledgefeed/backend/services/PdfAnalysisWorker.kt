package com.example.aiknowledgefeed.backend.services

import android.content.Context
import androidx.work.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class PdfAnalysisWorker(context:Context, parameters:WorkerParameters):CoroutineWorker(context,parameters) {
 override suspend fun doWork():Result {
  val id=inputData.getString("document_id")?:return Result.failure()
  val repo=LocalKnowledgeRepository(LocalDatabase.open(applicationContext))
  val settings=try{SecureStore(applicationContext).settings()}catch(e:CancellationException){throw e}catch(_:Exception){return Result.failure()}
  val queue=AnalysisQueue(repo,LocalPdfService(applicationContext,repo,DirectModelClient()))
  try {
   queue.run(id,settings)
   val task=repo.dao.analysisTasks().firstOrNull{it.documentId==id}
   return if(task?.state=="running")Result.retry() else Result.success()
  }catch(e:CancellationException){throw e}catch(e:Exception){
   val code=FailureMessages.code(e)
   if(code in setOf("llm_unavailable","llm_timeout","llm_rate_limit","llm_server_error")&&runAttemptCount<2){
    repo.dao.analysisTasks().firstOrNull{it.documentId==id}?.let{repo.dao.analysisTask(it.copy(state="queued"))};repo.changed()
    return Result.retry()
   }
   return Result.failure()
  }
 }
 companion object {
  fun enqueue(context:Context,id:String) {
   val work=OneTimeWorkRequestBuilder<PdfAnalysisWorker>().setInputData(workDataOf("document_id" to id))
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
   WorkManager.getInstance(context).enqueueUniqueWork("pdf-analysis:$id",ExistingWorkPolicy.KEEP,work)
  }
 }
}
