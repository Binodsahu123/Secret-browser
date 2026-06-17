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
    suspend fun queryHistorySemantic(query: String): List<HistoryEntity>
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

    override suspend fun queryHistorySemantic(query: String): List<HistoryEntity> {
        val basic = queryHistory(query).toMutableList()
        val semanticMatch = HistorySearch.classifySemanticIntent(query)
        if (semanticMatch.isNotEmpty()) {
            val allHistory = getHistoryPagedList()
            for (item in allHistory) {
                if (basic.none { it.id == item.id }) {
                    for (keyword in semanticMatch) {
                        if (item.url.contains(keyword, ignoreCase = true) || item.title.contains(keyword, ignoreCase = true)) {
                            basic.add(item)
                            break
                        }
                    }
                }
            }
        }
        return basic
    }

    private suspend fun getHistoryPagedList(): List<HistoryEntity> {
        return try {
            db.historyDao().getHistoryPaged(100, 0)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

object HistorySearch {
    /**
     * AI-assisted semantic classification mapping search intent/theme terms to domain keywords.
     */
    fun classifySemanticIntent(query: String): List<String> {
        val normalized = query.trim().lowercase()
        return when {
            normalized.contains("tech") || normalized.contains("code") || normalized.contains("developer") ->
                listOf("github", "stackoverflow", "medium", "reddit", "kotlin", "google")
            normalized.contains("social") || normalized.contains("chat") || normalized.contains("friends") ->
                listOf("twitter", "facebook", "instagram", "linkedin", "reddit", "tiktok")
            normalized.contains("finance") || normalized.contains("banking") || normalized.contains("money") ->
                listOf("paypal", "visa", "bank", "stripe", "chase", "finance")
            normalized.contains("mail") || normalized.contains("workspace") || normalized.contains("office") ->
                listOf("gmail", "yahoo", "outlook", "mail", "drive", "docs")
            normalized.contains("video") || normalized.contains("watch") || normalized.contains("movie") || normalized.contains("stream") ->
                listOf("youtube", "netflix", "twitch", "vimeo", "disney", "prime")
            else -> emptyList()
        }
    }

    fun rankSuggestions(history: List<HistoryEntity>, query: String): List<HistoryEntity> {
        val direct = history.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
        val semantic = classifySemanticIntent(query)
        if (semantic.isEmpty()) return direct

        val items = direct.toMutableList()
        for (item in history) {
            if (items.none { it.id == item.id }) {
                if (semantic.any { item.url.contains(it, ignoreCase = true) }) {
                    items.add(item)
                }
            }
        }
        return items
    }
}
