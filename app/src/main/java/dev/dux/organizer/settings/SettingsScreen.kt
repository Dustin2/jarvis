package dev.dux.organizer.settings

import dev.dux.organizer.ask.ModelState
import dev.dux.organizer.ask.MODEL_BYTES
import dev.dux.organizer.ask.AskJarvis
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.LinearProgressIndicator
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
import dev.dux.organizer.backup.AutoBackup
import dev.dux.organizer.backup.AutoBackupState
import dev.dux.organizer.ui.SPANISH
import dev.dux.organizer.ui.inlineLabel
import dev.dux.organizer.ui.rememberToday
import android.provider.DocumentsContract
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

class SettingsViewModel(
    private val backups: BackupRepository,
    private val resolver: ContentResolver,
    private val autoBackup: AutoBackup,
    private val ask: AskJarvis,
) : ViewModel() {
    /** null = still loading. */
    val auto = autoBackup.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun enableAutoBackup(folder: Uri) = run(R.string.auto_backup_failed) {
        message = Message(if (autoBackup.enable(folder)) R.string.auto_backup_enabled else R.string.auto_backup_failed)
    }

    fun backUpNow() = run(R.string.auto_backup_failed) {
        message = Message(if (autoBackup.runNow()) R.string.export_done else R.string.auto_backup_failed)
    }

    fun disableAutoBackup() = run(R.string.auto_backup_failed) {
        autoBackup.disable()
        message = Message(R.string.auto_backup_disabled)
    }

    val model = ask.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun downloadModel() = run(R.string.model_failed) { ask.download() }

    fun deleteModel() = run(R.string.model_failed) { ask.delete() }

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
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::enableAutoBackup)
    }
    val auto by vm.auto.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()

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
            // Automatic first: it is the one that protects the data without remembering to do anything.
            auto?.let { AutoBackupSection(it, vm, onPickFolder = { folderLauncher.launch(null) }) }
            model?.let { ModelSection(it, vm) }
            Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp).asHeading())
            Text(stringResource(R.string.backup_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
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
            text = { Text(stringResource(R.string.restore_confirm_body, data.tasks.size, data.projects.size, data.shoppingItems.size, data.movements.size, data.debts.size)) },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(SPANISH)

@Composable
private fun AutoBackupSection(state: AutoBackupState, vm: SettingsViewModel, onPickFolder: () -> Unit) {
    val today by rememberToday()
    Text(stringResource(R.string.auto_backup), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
    Text(stringResource(R.string.auto_backup_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
    val folder = state.folder
    if (folder == null) {
        Button(onClick = onPickFolder, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_folder)) }
        return
    }
    // "primary:Documents/Jarvis" → "Documents/Jarvis"; other providers may only give an opaque id.
    Text(stringResource(R.string.auto_backup_folder, DocumentsContract.getTreeDocumentId(folder).substringAfter(':')))
    val last = state.lastSuccess?.atZone(ZoneId.systemDefault())
    Text(
        if (last == null) stringResource(R.string.auto_backup_never)
        else stringResource(R.string.auto_backup_last, last.toLocalDate().inlineLabel(today), last.format(time)),
    )
    if (state.lastFailure != null) Text(stringResource(R.string.auto_backup_problem), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = vm::backUpNow, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_up_now)) }
    OutlinedButton(onClick = onPickFolder, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_folder)) }
    TextButton(onClick = vm::disableAutoBackup, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.turn_off_auto_backup)) }
}

@Composable
private fun ModelSection(state: ModelState, vm: SettingsViewModel) {
    Text(stringResource(R.string.ask_jarvis), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp).asHeading())
    Text(stringResource(R.string.model_explanation, MODEL_BYTES / 1_000_000_000.0), color = MaterialTheme.colorScheme.onSurfaceVariant)
    when (state) {
        ModelState.NotInstalled, ModelState.Failed -> {
            if (state == ModelState.Failed) Text(stringResource(R.string.model_download_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = vm::downloadModel, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.model_download)) }
        }
        is ModelState.Downloading -> {
            val progress = state.progress
            Text(
                when {
                    state.waitingForWifi -> stringResource(R.string.model_waiting_wifi)
                    progress == null -> stringResource(R.string.model_starting)
                    else -> stringResource(R.string.model_downloading, (progress * 100).toInt())
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (progress != null) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = vm::deleteModel, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        }
        ModelState.Installed -> {
            var confirmDelete by remember { mutableStateOf(false) }
            Text(stringResource(R.string.model_installed))
            TextButton(onClick = { confirmDelete = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.model_delete)) }
            // Getting it back means another 2 GB download.
            if (confirmDelete) AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.model_delete_title)) },
                text = { Text(stringResource(R.string.model_delete_body)) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteModel() }) { Text(stringResource(R.string.model_delete)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
    Text(stringResource(R.string.model_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
