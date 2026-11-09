package com.example.aiknowledgefeed.backend.services

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import kotlinx.coroutines.CancellationException

/** Local work has no network or credential constraint. Original is retained privately. */
class PdfExtractionWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
 override suspend fun doWork():Result {
  val id=inputData.getString("document_id")?:return Result.failure()
  val repo=LocalKnowledgeRepository(LocalDatabase.open(applicationContext))
  val row=repo.dao.documents().firstOrNull{it.id==id}?:return Result.success()
  if(row.status!="imported")return Result.success()
  try {
   val file=java.io.File(applicationContext.noBackupFilesDir,"pdfs/$id.pdf")
   if(file.length()>10*1024*1024)throw ConnectionFailure("upload_too_large")
   val service=LocalPdfService(applicationContext,repo,DirectModelClient())
   val bytes=try{file.readBytes()}catch(_:java.io.IOException){throw ConnectionFailure("local_storage")}
   service.importDocument(bytes,row.filename,expectedDocumentId=id)
   val settings=try{SecureStore(applicationContext).settings()}catch(e:CancellationException){throw e}catch(_:Exception){com.example.aiknowledgefeed.backend.models.Settings()}
   AnalysisQueue(repo,service).reconcile(settings).forEach{PdfAnalysisWorker.enqueue(applicationContext,it)}
   return Result.success()
  }catch(e:CancellationException){throw e}catch(e:Exception){
   repo.db.withTransaction {
   val current=repo.dao.documents().firstOrNull{it.id==id}?:return@withTransaction
   repo.dao.document(current.copy(status="error",message=FailureMessages.message(FailureMessages.code(e))))
   repo.dao.analysisTasks().firstOrNull{it.documentId==id}?.let{repo.dao.analysisTask(it.copy(state="error",error=FailureMessages.message(FailureMessages.code(e))))};repo.changed()
   }
   return Result.failure()
  }
 }
 companion object {
  fun enqueue(context:Context,id:String){WorkManager.getInstance(context).enqueueUniqueWork("pdf-extraction:$id",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<PdfExtractionWorker>().setInputData(workDataOf("document_id" to id)).build())}
 }
}
