package com.example.aiknowledgefeed.backend.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName = "profile")
data class ProfileRow(@PrimaryKey val id: Int = 1, val json: String)
@Entity(tableName = "documents", indices = [Index(value = ["sha256"], unique = true)])
data class DocumentRow(@PrimaryKey val id: String, val filename: String, val status: String, val message: String?, val sha256: String? = null)
@Entity(tableName = "chunks", foreignKeys = [ForeignKey(entity = DocumentRow::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("documentId")])
data class ChunkRow(@PrimaryKey val id: String, val documentId: String, val page: Int, val position: Int, val text: String)
@Entity(tableName = "knowledge", indices = [Index(value = ["normalizedName"], unique = true), Index("sourcePaperId")])
data class KnowledgeRow(@PrimaryKey val id: String, val name: String, val normalizedName: String, val description: String, val status: String, val sourcePaperId: String? = null)
@Entity(tableName = "concept_links", primaryKeys = ["conceptId", "relatedId"], foreignKeys = [ForeignKey(entity = KnowledgeRow::class, parentColumns = ["id"], childColumns = ["conceptId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = KnowledgeRow::class, parentColumns = ["id"], childColumns = ["relatedId"], onDelete = ForeignKey.CASCADE)], indices = [Index("relatedId")])
data class LinkRow(val conceptId: String, val relatedId: String)
@Entity(tableName = "evidence", primaryKeys = ["documentId", "conceptId"], foreignKeys = [ForeignKey(entity = DocumentRow::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = KnowledgeRow::class, parentColumns = ["id"], childColumns = ["conceptId"], onDelete = ForeignKey.CASCADE)], indices = [Index("conceptId")])
data class EvidenceRow(val documentId: String, val conceptId: String, val pages: String)
@Entity(tableName = "papers", indices = [Index(value = ["doi"], unique = true), Index(value = ["arxivId"], unique = true)])
data class PaperRow(@PrimaryKey val id: String, val doi: String?, val arxivId: String?, val json: String)
@Entity(tableName = "feeds")
data class FeedRow(@PrimaryKey val day: String, val status: String, val json: String? = null, val attempts: Int = 0, val retryAt: Long = 0, val leaseUntil: Long = 0)
@Entity(tableName = "seen")
data class SeenRow(@PrimaryKey val identifier: String)
@Entity(tableName = "metadata")
data class MetadataRow(@PrimaryKey val key: String, val value: String)

@Dao
interface LocalDao {
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun profile(): ProfileRow?
    @Upsert suspend fun profile(row: ProfileRow)
    @Query("SELECT * FROM knowledge ORDER BY rowid") suspend fun knowledge(): List<KnowledgeRow>
    @Upsert suspend fun knowledge(row: KnowledgeRow)
    @Query("DELETE FROM knowledge WHERE id = :id") suspend fun deleteKnowledge(id: String)
    @Query("SELECT * FROM concept_links") suspend fun links(): List<LinkRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(row: LinkRow)
    @Query("SELECT * FROM evidence") suspend fun evidence(): List<EvidenceRow>
    @Upsert suspend fun evidence(row: EvidenceRow)
    @Query("SELECT * FROM documents ORDER BY rowid") suspend fun documents(): List<DocumentRow>
    @Upsert suspend fun document(row: DocumentRow)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: String)
    @Query("SELECT * FROM chunks WHERE documentId = :id ORDER BY page, position") suspend fun chunks(id: String): List<ChunkRow>
    @Insert suspend fun chunks(rows: List<ChunkRow>)
    @Query("SELECT * FROM papers WHERE id = :id") suspend fun paper(id: String): PaperRow?
    @Query("SELECT * FROM papers") suspend fun papers(): List<PaperRow>
    @Query("SELECT * FROM papers WHERE id = :id OR doi = :doi OR arxivId = :arxiv LIMIT 1") suspend fun matchingPaper(id: String, doi: String?, arxiv: String?): PaperRow?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun paper(row: PaperRow): Long
    @Update suspend fun updatePaper(row: PaperRow)
    @Query("SELECT * FROM feeds WHERE day = :day") suspend fun feed(day: String): FeedRow?
    @Query("SELECT * FROM feeds WHERE status = 'success' ORDER BY day DESC") suspend fun successfulFeeds(): List<FeedRow>
    @Query("SELECT * FROM feeds WHERE status = 'success' ORDER BY day DESC LIMIT 1") suspend fun latestSuccessfulFeed(): FeedRow?
    @Query("SELECT * FROM feeds WHERE status = 'success' AND json LIKE :pattern ORDER BY day DESC") suspend fun matchingFeeds(pattern: String): List<FeedRow>
    @Query("SELECT COUNT(*) FROM feeds WHERE status = 'success'") suspend fun successfulFeedCount(): Int
    @Upsert suspend fun feed(row: FeedRow)
    @Query("SELECT identifier FROM seen") suspend fun seen(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seen(row: SeenRow)
    @Query("SELECT value FROM metadata WHERE `key` = :key") suspend fun metadata(key: String): String?
    @Upsert suspend fun metadata(row: MetadataRow)
    @Query("SELECT value FROM metadata WHERE `key` = 'revision'") fun observeRevision(): Flow<String?>
}

@Database(entities = [ProfileRow::class, DocumentRow::class, ChunkRow::class, KnowledgeRow::class, LinkRow::class, EvidenceRow::class, PaperRow::class, FeedRow::class, SeenRow::class, MetadataRow::class], version = 1, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao
    companion object {
        @Volatile private var instance: LocalDatabase? = null
        fun open(context: Context): LocalDatabase = instance ?: synchronized(this) {
            instance ?: create(context, File(context.noBackupFilesDir, "paperview.db").absolutePath).also { instance = it }
        }
        fun create(context: Context, path: String): LocalDatabase = Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, path).build()
        fun inMemory(context: Context): LocalDatabase = Room.inMemoryDatabaseBuilder(context.applicationContext, LocalDatabase::class.java).build()
    }
}
