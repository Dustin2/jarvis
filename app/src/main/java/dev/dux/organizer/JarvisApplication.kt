package dev.dux.organizer

import android.app.Application
import android.content.Context
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.BackupRepository
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.ShoppingRepository
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.reminders.Reminders
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency injection: the single place where the database and repositories are created. */
class JarvisApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val reminders by lazy { Reminders(this, database.tasks()) }
    val tasks by lazy { TaskRepository(database.tasks(), reminders = reminders) }
    val projects by lazy { ProjectRepository(database.projects()) }
    val shopping by lazy { ShoppingRepository(database.shopping()) }
    val backups by lazy { BackupRepository(database, File(filesDir, "backups"), reminders = reminders) }

    override fun onCreate() {
        super.onCreate()
        reminders.createChannel()
        MainScope().launch {
            projects.seedIfEmpty()
            shopping.seedIfEmpty()
            // Force-stopping the app silently clears its alarms; opening it restores them (and notifies missed ones).
            reminders.syncAll()
        }
    }
}

val Context.app: JarvisApplication get() = applicationContext as JarvisApplication
