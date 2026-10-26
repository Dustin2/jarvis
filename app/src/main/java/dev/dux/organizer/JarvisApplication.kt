package dev.dux.organizer

import android.app.Application
import android.content.Context
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.BackupRepository
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.reminders.Reminders
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

/** Inyección de dependencias manual: un único lugar donde se crean la base y los repositorios. */
class JarvisApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val reminders by lazy { Reminders(this, database.tasks()) }
    val tasks by lazy { TaskRepository(database.tasks(), reminders = reminders) }
    val projects by lazy { ProjectRepository(database.projects()) }
    val backups by lazy { BackupRepository(database, File(filesDir, "backups"), reminders = reminders) }

    override fun onCreate() {
        super.onCreate()
        reminders.createChannel()
        MainScope().launch {
            projects.seedIfEmpty()
            // Forzar el cierre de la app borra sus alarmas sin avisar; al abrirla se reponen (y se avisa lo atrasado).
            reminders.syncAll()
        }
    }
}

val Context.app: JarvisApplication get() = applicationContext as JarvisApplication
