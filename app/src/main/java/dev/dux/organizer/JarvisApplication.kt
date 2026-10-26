package dev.dux.organizer

import android.app.Application
import android.content.Context
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.TaskRepository

/** Inyección de dependencias manual: un único lugar donde se crean la base y los repositorios. */
class JarvisApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val tasks by lazy { TaskRepository(database.tasks()) }
}

val Context.app: JarvisApplication get() = applicationContext as JarvisApplication
