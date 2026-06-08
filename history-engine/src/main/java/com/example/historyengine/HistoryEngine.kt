package com.example.historyengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "engine_history",
    indices = [
        Index(value = ["url"]),
        Index(value = ["title"]),
        Index(value = ["timestamp"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM engine_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM engine_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPaged(limit: Int, offset: Int): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(items: List<HistoryEntity>)

    @Query("DELETE FROM engine_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM engine_history WHERE title LIKE :query OR url LIKE :query ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchHistory(query: String): List<HistoryEntity>
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class HistoryLocalDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

interface HistoryEngine {
    fun getHistoryFlow(): Flow<List<HistoryEntity>>
    suspend fun addHistoryItem(url: String, title: String)
    suspend fun clearAllHistory()
    suspend fun queryHistory(query: String): List<HistoryEntity>
}

class HistoryRepository(private val context: Context) : HistoryEngine {
    private val db: HistoryLocalDatabase by lazy {
        com.example.databasecore.DatabaseCore.buildDatabase(
            context,
            HistoryLocalDatabase::class.java,
            "orion_history_engine_database"
        )
    }

    override fun getHistoryFlow(): Flow<List<HistoryEntity>> {
        return db.historyDao().getAllHistoryFlow()
    }

    override suspend fun addHistoryItem(url: String, title: String) {
        db.historyDao().insert(HistoryEntity(url = url, title = title))
    }

    override suspend fun clearAllHistory() {
        db.historyDao().clearHistory()
    }

    override suspend fun queryHistory(query: String): List<HistoryEntity> {
        return db.historyDao().searchHistory("%$query%")
    }
}

class HistorySearch {
    fun rankSuggestions(history: List<HistoryEntity>, query: String): List<HistoryEntity> {
        return history.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
    }
}
