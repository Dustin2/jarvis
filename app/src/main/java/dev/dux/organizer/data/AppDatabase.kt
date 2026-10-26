package dev.dux.organizer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Cada cambio de esquema sube la versión y agrega una migración probada. Nunca fallbackToDestructiveMigration.
@Database(entities = [Task::class, Project::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "organizer.db").build()
    }
}
