package dev.dux.organizer

import android.app.Application
import android.content.Context
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.BackupRepository
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.TaskRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

/** Inyección de dependencias manual: un único lugar donde se crean la base y los repositorios. */
class JarvisApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val tasks by lazy { TaskRepository(database.tasks()) }
    val projects by lazy { ProjectRepository(database.projects()) }
    val backups by lazy { BackupRepository(database, File(filesDir, "backups")) }

    override fun onCreate() {
        super.onCreate()
        MainScope().launch { projects.seedIfEmpty() }
    }
}

val Context.app: JarvisApplication get() = applicationContext as JarvisApplication
