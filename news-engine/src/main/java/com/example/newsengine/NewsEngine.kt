package com.example.newsengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "engine_news")
data class NewsItemEntity(
    @PrimaryKey val link: String,
    val title: String,
    val description: String,
    val pubDate: String,
    val category: String,
    val cachedTime: Long = System.currentTimeMillis()
)

@Dao
interface NewsDao {
    @Query("SELECT * FROM engine_news ORDER BY cachedTime DESC")
    fun getAllNewsFlow(): Flow<List<NewsItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NewsItemEntity>)

    @Query("DELETE FROM engine_news WHERE cachedTime < :expiry")
    suspend fun deleteOldNews(expiry: Long)
}

@Database(entities = [NewsItemEntity::class], version = 1, exportSchema = false)
abstract class NewsLocalDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
}

interface NewsEngine {
    fun getNewsFlow(): Flow<List<NewsItemEntity>>
    suspend fun refreshNews(rssUrl: String, category: String)
}

class NewsRepository(private val context: Context) : NewsEngine {
    private val db: NewsLocalDatabase by lazy {
        com.example.databasecore.DatabaseCore.buildDatabase(
            context,
            NewsLocalDatabase::class.java,
            "orion_news_engine_database"
        )
    }

    override fun getNewsFlow(): Flow<List<NewsItemEntity>> = db.newsDao().getAllNewsFlow()

    override suspend fun refreshNews(rssUrl: String, category: String) {
        // News logic
    }
}

class NewsCache(private val context: Context) {
    suspend fun clearOldCache() {
        // cache logic
    }
}
