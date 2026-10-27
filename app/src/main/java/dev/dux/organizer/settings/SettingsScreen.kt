package dev.dux.organizer.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.R
import dev.dux.organizer.data.BackupData
import dev.dux.organizer.data.BackupRepository
import dev.dux.organizer.data.InvalidBackupException
import dev.dux.organizer.data.decodeBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import dev.dux.organizer.ui.asHeading

/** [detail] is already user-facing Spanish text (e.g. why a backup is invalid). */
data class Message(@StringRes val text: Int, val detail: String? = null)

// A real backup is a few kilobytes; the limit avoids running out of memory if the wrong file is picked.
private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

class SettingsViewModel(private val backups: BackupRepository, private val resolver: ContentResolver) : ViewModel() {
    var message by mutableStateOf<Message?>(null)
        private set
    /** Already validated backup, waiting for the user to confirm the replacement. */
    var pendingRestore by mutableStateOf<BackupData?>(null)
        private set
    var canUndo by mutableStateOf(backups.canUndoRestore)
        private set
    var busy by mutableStateOf(false)
        private set

    fun export(uri: Uri) = run(R.string.export_failed) {
        val json = backups.export()
        withContext(Dispatchers.IO) {
            // "wt" truncates: with "w" some providers leave garbage at the end if the file already existed.
            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("sin acceso al archivo")
        }
        message = Message(R.string.export_done)
    }

    fun read(uri: Uri) = run(R.string.restore_failed) {
        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readNBytes(MAX_BACKUP_BYTES + 1) } ?: error("sin acceso al archivo")
        }
        if (bytes.size > MAX_BACKUP_BYTES) throw InvalidBackupException("El archivo es demasiado grande para ser un respaldo.")
        pendingRestore = withContext(Dispatchers.Default) { decodeBackup(bytes.decodeToString()) }
    }

    fun confirmRestore() {
        val data = pendingRestore ?: return
        pendingRestore = null
        run(R.string.restore_failed) {
            backups.restore(data)
            canUndo = true
            message = Message(R.string.restore_done)
        }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun undo() = run(R.string.restore_failed) {
        backups.undoRestore()
        canUndo = false
        message = Message(R.string.undo_done)
    }

    fun messageShown() {
        message = null
    }

    private fun run(@StringRes failure: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: InvalidBackupException) {
                message = Message(R.string.invalid_backup, e.message)
            } catch (e: Exception) {
                message = Message(failure, e.message)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    // Some file managers do not recognize .json and report it as binary or text.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::read)
    }

    vm.message?.let { message ->
        val text = stringResource(message.text) + (message.detail?.let { "\n$it" } ?: "")
        LaunchedEffect(message) {
            snackbar.showSnackbar(text)
            vm.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
            Text(stringResource(R.string.backup_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { exportLauncher.launch("jarvis-respaldo-${LocalDate.now()}.json") },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.export_backup)) }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.restore_backup)) }
            if (vm.canUndo) {
                TextButton(onClick = vm::undo, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.undo_restore))
                }
            }
        }
    }

    vm.pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body, data.tasks.size, data.projects.size)) },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
