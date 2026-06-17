package com.example.databasecore

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object DatabaseCore {
    fun <T : RoomDatabase> buildDatabase(
        context: Context,
        dbClass: Class<T>,
        name: String
    ): T {
        return Room.databaseBuilder(
            context.applicationContext,
            dbClass,
            name
        )
        .fallbackToDestructiveMigration()
        .build()
    }
}
