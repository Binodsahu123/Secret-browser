package com.example.downloadengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "engine_downloads")
data class DownloadEntity(
    @PrimaryKey val id: Long,
    val fileName: String,
    val url: String,
    val status: String, // "PENDING", "DOWNLOADING", "COMPLETE", "FAILED"
    val progress: Int = 0,
    val mimeType: String = "application/octet-stream"
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM engine_downloads ORDER BY id DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("SELECT * FROM engine_downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?
}

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class DownloadLocalDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}

interface DownloadEngine {
    fun getDownloadsFlow(): Flow<List<DownloadEntity>>
    suspend fun startDownload(url: String, fileName: String, mimeType: String): Long
    suspend fun updateDownloadStatus(id: Long, status: String, progress: Int)
}

class DownloadManager(private val context: Context) : DownloadEngine {
    private val db: DownloadLocalDatabase by lazy {
        com.example.databasecore.DatabaseCore.buildDatabase(
            context,
            DownloadLocalDatabase::class.java,
            "orion_downloads_engine_database"
        )
    }

    override fun getDownloadsFlow(): Flow<List<DownloadEntity>> {
        return db.downloadDao().getAllDownloadsFlow()
    }

    override suspend fun startDownload(url: String, fileName: String, mimeType: String): Long {
        val id = System.currentTimeMillis()
        db.downloadDao().insert(DownloadEntity(id, fileName, url, "DOWNLOADING", 0, mimeType))
        return id
    }

    override suspend fun updateDownloadStatus(id: Long, status: String, progress: Int) {
        val existing = db.downloadDao().getDownloadById(id)
        if (existing != null) {
            db.downloadDao().insert(existing.copy(status = status, progress = progress))
        }
    }
}

class DownloadWorkers {
    fun enqueueDownloadCheck() {
        // Job schedule placeholder
    }
}
