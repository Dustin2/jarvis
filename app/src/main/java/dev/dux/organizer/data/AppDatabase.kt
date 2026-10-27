package dev.dux.organizer.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Every schema change bumps the version and adds a tested migration. Never fallbackToDestructiveMigration.
// v2: tasks.reminderAt. v3: tasks.recurrence and tasks.seriesId.
// v4: shopping_lists and shopping_items.
// They only add nullable columns or new tables, so Room's auto-migrations are enough.
@Database(
    entities = [Task::class, Project::class, ShoppingList::class, ShoppingItem::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao
    abstract fun shopping(): ShoppingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "organizer.db").build()
    }
}
