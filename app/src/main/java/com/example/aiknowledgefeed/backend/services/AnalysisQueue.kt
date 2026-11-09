package com.example.aiknowledgefeed.backend.services

import androidx.room.withTransaction
import com.example.aiknowledgefeed.backend.database.*
import com.example.aiknowledgefeed.backend.models.Settings
import com.example.aiknowledgefeed.backend.repositories.*
import kotlinx.coroutines.*

/** Room is authoritative; WorkManager contains IDs only. Consent is per document. */
class AnalysisQueue(private val repo: LocalKnowledgeRepository, private val pdf: LocalPdfService) {
 companion object { const val VERSION = 1; const val KEY_EPOCH = SettingsPersistence.KEY_EPOCH }
 private fun signature(settings:Settings)=java.security.MessageDigest.getInstance("SHA-256").digest((settings.apiKey+"|"+settings.model+"|"+settings.llmBaseUrl+"|"+settings.credentialEpoch+"|"+settings.directApiConsent).toByteArray()).joinToString(""){"%02x".format(it)}
 suspend fun reconcile(settings: Settings): List<String> = repo.db.withTransaction {
  val ready=mutableListOf<String>()
  repo.dao.documents().filter { it.status !in listOf("needs_text","imported","error") }.forEach { document ->
   var task=repo.dao.analysisTasks().firstOrNull { it.documentId==document.id }
   if(task==null) {
    if(document.status=="analyzed") return@forEach
    task=AnalysisTaskRow(document.id,System.currentTimeMillis())
   }
   if(task.version>=VERSION && document.status=="analyzed") return@forEach
   // An older analysis cannot satisfy a newer pipeline version.
   if(document.status=="analyzed" && task.version<VERSION) repo.dao.document(document.copy(status="extracted"))
   if(task.state=="running" && task.leaseUntil>System.currentTimeMillis()) return@forEach
   if(task.state=="error" && repo.dao.metadata("analysis-config:${document.id}")==signature(settings)) return@forEach
   val state=when {
    settings.apiKey.isBlank() || settings.model.isBlank() || settings.credentialEpoch<KEY_EPOCH -> "waiting_key"
    !task.consent || !settings.directApiConsent -> "waiting_consent"
    else -> "queued"
   }
   repo.dao.analysisTask(task.copy(state=state,leaseUntil=0));if(state=="queued")ready+=document.id
  };repo.changed();ready
 }
 suspend fun consent(id:String, force:Boolean=false) = repo.db.withTransaction {
  val doc=repo.dao.documents().firstOrNull{it.id==id}?:throw ConnectionFailure("not_found")
  var task=repo.dao.analysisTasks().firstOrNull{it.documentId==id}?:AnalysisTaskRow(id,System.currentTimeMillis())
  if(task.state=="running"&&task.leaseUntil>System.currentTimeMillis())throw ConnectionFailure("feed_busy")
  if(force){repo.dao.document(doc.copy(status="extracted"));task=task.copy(version=0)}
  repo.dao.analysisTask(task.copy(consent=true,state="queued",error=null,leaseUntil=0));repo.changed()
 }
 suspend fun run(id:String,settings:Settings): Boolean {
  reconcile(settings)
  val claimed=repo.db.withTransaction {
   val task=repo.dao.analysisTasks().firstOrNull{it.documentId==id}?:return@withTransaction false
   if(task.state!="queued")return@withTransaction false
   repo.dao.metadata(MetadataRow("analysis-config:$id",signature(settings)))
   repo.dao.analysisTask(task.copy(state="running",model=settings.model,leaseUntil=System.currentTimeMillis()+120000));repo.changed();true
  }
  if(!claimed)return false
  try {
   pdf.analyze(id,true,settings)
   repo.db.withTransaction {
    val task=repo.dao.analysisTasks().firstOrNull{it.documentId==id}?:return@withTransaction
    repo.dao.analysisTask(task.copy(state="awaiting_confirmation",version=VERSION,model=settings.model,leaseUntil=0,error=null));repo.changed()
   };return true
  } catch(e:Exception) {
   withContext(NonCancellable) { repo.db.withTransaction {
    repo.dao.analysisTasks().firstOrNull{it.documentId==id}?.let { repo.dao.analysisTask(it.copy(state=if(e is CancellationException)"queued" else "error",leaseUntil=0,error=if(e is CancellationException)null else FailureMessages.message(FailureMessages.code(e)))) };repo.changed()
   } };throw e
  }
 }
}
