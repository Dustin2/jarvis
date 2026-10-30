package dev.dux.organizer

import android.app.Application
import android.content.Context
import android.graphics.drawable.Icon
import android.content.pm.ShortcutManager
import android.content.pm.ShortcutInfo
import android.content.Intent
import dev.dux.organizer.ask.AskJarvis
import dev.dux.organizer.backup.AutoBackup
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.BackupRepository
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.ShoppingRepository
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.reminders.Reminders
import androidx.glance.appwidget.updateAll
import dev.dux.organizer.widget.TodayWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency injection: the single place where the database and repositories are created. */
class JarvisApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val reminders by lazy { Reminders(this, database.tasks()) }
    val tasks by lazy { TaskRepository(database.tasks(), reminders = reminders) }
    val projects by lazy { ProjectRepository(database.projects()) }
    val shopping by lazy { ShoppingRepository(database.shopping()) }
    val finance by lazy { FinanceRepository(database.finance()) }
    val backups by lazy { BackupRepository(database, File(filesDir, "backups"), reminders = reminders) }
    val autoBackup by lazy { AutoBackup(this, backups) }
    val askJarvis by lazy { AskJarvis(this) }

    override fun onCreate() {
        super.onCreate()
        reminders.createChannel()
        addAskShortcut()
        MainScope().launch {
            // First start only: the starting lists and categories in the phone's language (then they are the user's data).
            projects.seedIfEmpty(resources.getStringArray(R.array.seed_projects).toList())
            shopping.seedIfEmpty(getString(R.string.seed_list))
            finance.seedIfEmpty(resources.getStringArray(R.array.seed_expense_categories).toList(), resources.getStringArray(R.array.seed_income_categories).toList())
            autoBackup.scheduleIfEnabled()
            // Force-stopping the app silently clears its alarms; opening it restores them (and notifies missed ones).
            reminders.syncAll()
        }
        // Keeps the home screen widget in step with any change to open tasks, wherever it is made.
        MainScope().launch {
            tasks.open.drop(1).collectLatest { TodayWidget().updateAll(this@JarvisApplication) }
        }
    }
}

/** Long-press on the app icon → "Hablar con Jarvis". Dynamic, not XML, so it targets this build's package (".debug" too). */
private fun Context.addAskShortcut() {
    val shortcut = ShortcutInfo.Builder(this, "ask_voice")
        .setShortLabel(getString(R.string.ask_shortcut))
        .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_ask))
        .setIntent(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_ASK_VOICE))
        .build()
    getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(shortcut)
}

val Context.app: JarvisApplication get() = applicationContext as JarvisApplication
