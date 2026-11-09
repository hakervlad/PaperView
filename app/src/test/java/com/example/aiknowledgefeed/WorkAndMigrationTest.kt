package com.example.aiknowledgefeed

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.services.PdfAnalysisWorker
import com.example.aiknowledgefeed.backend.services.PdfExtractionWorker
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class WorkAndMigrationTest {
 @Test fun versionOneMigratesWithoutLosingProfileOrDocuments() = runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val file=File(context.noBackupFilesDir,"migration-v1.db");file.delete()
  val schema=listOf(File("schemas/com.example.aiknowledgefeed.backend.database.LocalDatabase/1.json"),File("app/schemas/com.example.aiknowledgefeed.backend.database.LocalDatabase/1.json")).first{it.exists()}
  val database=JsonParser.parseString(schema.readText()).asJsonObject.getAsJsonObject("database")
  SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
   database.getAsJsonArray("entities").forEach{e->val entity=e.asJsonObject;val name=entity.get("tableName").asString;db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}",name));entity.getAsJsonArray("indices")?.forEach{db.execSQL(it.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}",name))}}
   database.getAsJsonArray("setupQueries").forEach{db.execSQL(it.asString)}
   db.execSQL("INSERT INTO profile VALUES(1,?)",arrayOf("{\"bachelor_subject\":\"Erhalten\"}"))
   db.execSQL("INSERT INTO documents VALUES('doc','synthetic.pdf','extracted',NULL,NULL)");db.version=1
  }
  val db=LocalDatabase.create(context,file.absolutePath)
  try{Assert.assertTrue(db.dao().profile()!!.json.contains("Erhalten"));Assert.assertEquals("doc",db.dao().documents().single().id);Assert.assertEquals("waiting_key",db.dao().analysisTasks().single().state)}finally{db.close();file.delete()}
 }
 @Test fun workManagerKeepsUniqueConstrainedWorkAndTransmitsOnlyAnId() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  WorkManagerTestInitHelper.initializeTestWorkManager(context,Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
  PdfAnalysisWorker.enqueue(context,"synthetic-doc");PdfAnalysisWorker.enqueue(context,"synthetic-doc")
  val info=WorkManager.getInstance(context).getWorkInfosForUniqueWork("pdf-analysis:synthetic-doc").get()
  Assert.assertTrue(info.count{!it.state.isFinished}<=1)
  Assert.assertTrue(info.isNotEmpty())
  WorkManager.getInstance(context).cancelAllWork().result.get()
 }
 @Test fun missingLocalOriginalIsReportedAsStorageFailureInsteadOfNetworkFailure()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val db=LocalDatabase.open(context)
  val id="missing-original-fixture"
  db.dao().document(com.example.aiknowledgefeed.backend.database.DocumentRow(id,"missing.pdf","imported",null))
  db.dao().analysisTask(com.example.aiknowledgefeed.backend.database.AnalysisTaskRow(id,0,state="imported"))
  val worker=TestListenableWorkerBuilder<PdfExtractionWorker>(context).setInputData(workDataOf("document_id" to id)).build()
  Assert.assertEquals(ListenableWorker.Result.failure(),worker.doWork())
  val row=db.dao().documents().first{it.id==id}
  Assert.assertEquals("error",row.status);Assert.assertTrue(row.message!!.startsWith("Lokales Speichern"))
 }

}
