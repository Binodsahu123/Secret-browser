package com.example.bookmarkengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "engine_bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String = "Bookmarks Bar"
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM engine_bookmarks ORDER BY title ASC")
    fun getAllBookmarksFlow(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Database(entities = [BookmarkEntity::class], version = 1, exportSchema = false)
abstract class BookmarkLocalDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
}

interface BookmarkEngine {
    fun getBookmarksFlow(): Flow<List<BookmarkEntity>>
    suspend fun addBookmark(url: String, title: String, folder: String = "Bookmarks Bar")
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}

class BookmarkRepository(private val context: Context) : BookmarkEngine {
    private val db: BookmarkLocalDatabase by lazy {
        com.example.databasecore.DatabaseCore.buildDatabase(
            context,
            BookmarkLocalDatabase::class.java,
            "orion_bookmarks_engine_database"
        )
    }

    override fun getBookmarksFlow(): Flow<List<BookmarkEntity>> {
        return db.bookmarkDao().getAllBookmarksFlow()
    }

    override suspend fun addBookmark(url: String, title: String, folder: String) {
        db.bookmarkDao().insert(BookmarkEntity(url = url, title = title, folder = folder))
    }

    override suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        db.bookmarkDao().delete(bookmark)
    }
}

class BookmarkFolders {
    fun getDefaultFolders(): List<String> = listOf("Bookmarks Bar", "Mobile Bookmarks", "Other Bookmarks")
}
