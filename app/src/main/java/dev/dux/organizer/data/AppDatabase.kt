package dev.dux.organizer.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Every schema change bumps the version and adds a tested migration. Never fallbackToDestructiveMigration.
// v2: tasks.reminderAt. It only adds a nullable column, so Room's auto-migration is enough.
@Database(
    entities = [Task::class, Project::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "organizer.db").build()
    }
}
