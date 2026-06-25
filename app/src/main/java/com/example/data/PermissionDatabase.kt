package com.example.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "site_permissions", indices = [Index(value = ["originUrl"], unique = false)])
data class PermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originUrl: String,
    val permissionType: String, // e.g. "MICROPHONE", "CAMERA", "GEOLOCATION", "FILE_SYSTEM"
    val permissionState: Int // 0 = ASK, 1 = ALLOWED, 2 = BLOCKED
)

@Dao
interface PermissionDao {
    @Query("SELECT permissionState FROM site_permissions WHERE originUrl = :origin AND permissionType = :type LIMIT 1")
    suspend fun getPermissionState(origin: String, type: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePermissionChoice(entity: PermissionEntity)

    @Query("DELETE FROM site_permissions WHERE originUrl = :origin")
    suspend fun deleteSettingsForOrigin(origin: String)
    
    @Query("SELECT * FROM site_permissions")
    suspend fun getAllPermissions(): List<PermissionEntity>
}

@Database(entities = [PermissionEntity::class], version = 1, exportSchema = false)
abstract class PermissionDatabase : RoomDatabase() {
    abstract fun permissionDao(): PermissionDao

    companion object {
        @Volatile
        private var INSTANCE: PermissionDatabase? = null

        fun getDatabase(context: Context): PermissionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PermissionDatabase::class.java,
                    "orion_chrome_site_permissions"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
