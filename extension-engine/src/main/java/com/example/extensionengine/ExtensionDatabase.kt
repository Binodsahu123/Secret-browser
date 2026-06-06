package com.example.extensionengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean,
    val manifestJson: String,
    val dateInstalled: Long = System.currentTimeMillis()
)

@Entity(tableName = "extension_storage", primaryKeys = ["extensionId", "area", "key"])
data class StorageEntity(
    val extensionId: String,
    val area: String, // "local" or "sync"
    val key: String,
    val valueJson: String
)

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions")
    fun getAllExtensionsFlow(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions")
    suspend fun getAllExtensions(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE id = :id")
    suspend fun getExtensionById(id: String): ExtensionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: ExtensionEntity)

    @Query("DELETE FROM extensions WHERE id = :id")
    suspend fun deleteExtensionById(id: String)

    @Query("UPDATE extensions SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabledState(id: String, enabled: Boolean)
}

@Dao
interface StorageDao {
    @Query("SELECT * FROM extension_storage WHERE extensionId = :extensionId AND area = :area")
    suspend fun getStorageByArea(extensionId: String, area: String): List<StorageEntity>

    @Query("SELECT * FROM extension_storage WHERE extensionId = :extensionId AND area = :area AND `key` IN (:keys)")
    suspend fun getStorageByKeys(extensionId: String, area: String, keys: List<String>): List<StorageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStorage(entities: List<StorageEntity>)

    @Query("DELETE FROM extension_storage WHERE extensionId = :extensionId AND area = :area AND `key` IN (:keys)")
    suspend fun deleteStorageByKeys(extensionId: String, area: String, keys: List<String>)

    @Query("DELETE FROM extension_storage WHERE extensionId = :extensionId AND area = :area")
    suspend fun clearStorage(extensionId: String, area: String)
}

@Database(entities = [ExtensionEntity::class, StorageEntity::class], version = 1, exportSchema = false)
abstract class ExtensionDatabase : RoomDatabase() {
    abstract fun extensionDao(): ExtensionDao
    abstract fun storageDao(): StorageDao

    companion object {
        @Volatile
        private var INSTANCE: ExtensionDatabase? = null

        fun getInstance(context: Context): ExtensionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExtensionDatabase::class.java,
                    "extension_engine.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
