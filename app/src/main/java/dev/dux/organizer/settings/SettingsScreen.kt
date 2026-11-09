package dev.dux.organizer.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import dev.dux.organizer.ui.screenEnter
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import dev.dux.organizer.ui.SectionTitle
import dev.dux.organizer.ui.blockRowShapes
import dev.dux.organizer.ui.inBlock
import dev.dux.organizer.ui.theme.LocalStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.dynamicLightColorScheme
import dev.dux.organizer.ui.Choice
import dev.dux.organizer.ui.theme.Appearance
import dev.dux.organizer.ui.theme.AppearanceSettings
import dev.dux.organizer.ui.theme.Palette
import dev.dux.organizer.ui.theme.Style
import dev.dux.organizer.ui.theme.ThemeMode

import dev.dux.organizer.reminders.BriefSettings
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import android.text.format.DateFormat
import android.Manifest
import java.time.LocalTime
import dev.dux.organizer.reminders.MorningBrief
import dev.dux.organizer.ui.appLocale
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
    private val morningBrief: MorningBrief,
    private val appearance: Appearance,
) : ViewModel() {
    /** null = still loading. */
    val look = appearance.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { appearance.setMode(mode) }
    fun setPalette(palette: Palette) = viewModelScope.launch { appearance.setPalette(palette) }
    fun setStyle(style: Style) = viewModelScope.launch { appearance.setStyle(style) }

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

    val brief = morningBrief.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBriefEnabled(enabled: Boolean) = viewModelScope.launch { morningBrief.setEnabled(enabled) }

    fun setWeekly(enabled: Boolean) = viewModelScope.launch { morningBrief.setWeekly(enabled) }

    fun setBriefTime(time: LocalTime) = viewModelScope.launch { morningBrief.setTime(time) }

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

/** One screen of Ajustes, opened from the index. */
private enum class Page(@StringRes val title: Int, @DrawableRes val icon: Int) {
    Appearance(R.string.appearance, R.drawable.ic_palette),
    Brief(R.string.brief_setting, R.drawable.ic_bell),
    Model(R.string.ask_jarvis, R.drawable.ic_ask),
    Backup(R.string.backup_restore, R.drawable.ic_backup),
    About(R.string.about, R.drawable.ic_info),
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val brief by vm.brief.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    BackHandler(page != null) { page = null }

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
                title = { page?.let { Text(stringResource(it.title)) } },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) page = null else onBack() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        // Forward slides in from the right, back from the left, like opening and closing a screen.
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            page,
            transitionSpec = { screenEnter(motion, forward = targetState != null) },
            modifier = Modifier.padding(padding),
            label = "settings page",
        ) { shown ->
            val scroll = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            val body = scroll.padding(horizontal = 24.dp, vertical = 8.dp)
            val spaced = Arrangement.spacedBy(12.dp)
            when (shown) {
                null -> Index(look, brief, model, auto, onOpen = { page = it }, modifier = scroll)
                Page.Appearance -> Column(body, verticalArrangement = spaced) { look?.let { AppearanceSection(it, vm) } }
                Page.Brief -> Column(body, verticalArrangement = spaced) { brief?.let { BriefSection(it, vm) } }
                Page.Model -> Column(body, verticalArrangement = spaced) { model?.let { ModelSection(it, vm) } }
                Page.Backup -> Column(body, verticalArrangement = spaced) {
                    // Automatic first: it is the one that protects the data without remembering to do anything.
                    auto?.let { AutoBackupSection(it, vm, onPickFolder = { folderLauncher.launch(null) }) }
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
                Page.About -> About(scroll)
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

/** The big title and the groups, each row saying what is set right now. */
@Composable
private fun Index(
    look: AppearanceSettings?,
    brief: BriefSettings?,
    model: ModelState?,
    auto: AutoBackupState?,
    onOpen: (Page) -> Unit,
    modifier: Modifier,
) {
    val today by rememberToday()
    val calm = LocalStyle.current == Style.Calm
    Column(modifier.padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.settings),
            style = if (calm) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
            fontWeight = if (calm) FontWeight.Light else FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).asHeading(),
        )
        SectionTitle(stringResource(R.string.settings_general))
        val appearance = look?.let { listOf(it.style.label, it.mode.label, it.palette.label).map { id -> stringResource(id) }.joinToString(" · ") }
        val briefText = brief?.let { if (it.enabled) stringResource(R.string.brief_at, timeText(it.time)) else stringResource(R.string.off) }
        Group(listOf(Page.Appearance to appearance, Page.Brief to briefText), onOpen)
        SectionTitle(stringResource(R.string.settings_assistant))
        val modelText = when (model) {
            ModelState.Installed -> stringResource(R.string.model_ready)
            is ModelState.Downloading -> stringResource(R.string.model_downloading_short)
            null -> null
            else -> stringResource(R.string.model_absent)
        }
        Group(listOf(Page.Model to modelText), onOpen)
        SectionTitle(stringResource(R.string.settings_data))
        val last = auto?.lastSuccess?.atZone(ZoneId.systemDefault())
        val backupText = when {
            auto == null -> null
            auto.folder == null -> stringResource(R.string.auto_backup_off_short)
            last == null -> stringResource(R.string.auto_backup_never)
            else -> stringResource(R.string.auto_backup_last, last.toLocalDate().inlineLabel(today), last.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale())))
        }
        Group(listOf(Page.Backup to backupText), onOpen)
        Spacer(Modifier.height(8.dp))
        Group(listOf(Page.About to stringResource(R.string.version, versionName())), onOpen)
    }
}

@Composable
private fun Group(rows: List<Pair<Page, String?>>, onOpen: (Page) -> Unit) {
    rows.forEachIndexed { i, (page, summary) ->
        ListItem(
            onClick = { onOpen(page) },
            shapes = blockRowShapes(),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { IconTile(page) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
            modifier = Modifier.inBlock(i, rows.lastIndex),
        ) { Text(stringResource(page.title)) }
    }
}

/** The icon on a tinted tile; each page keeps its own tint, all taken from the current palette. */
@Composable
private fun IconTile(page: Page) {
    val c = MaterialTheme.colorScheme
    val (container, content) = when (page) {
        Page.Appearance, Page.About -> c.tertiaryContainer to c.onTertiaryContainer
        Page.Brief, Page.Backup -> c.secondaryContainer to c.onSecondaryContainer
        Page.Model -> c.primaryContainer to c.onPrimaryContainer
    }
    Box(Modifier.size(40.dp).background(container, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
        Icon(painterResource(page.icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/** Name, version and credits. */
@Composable
private fun About(modifier: Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.version, versionName()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Text(
            stringResource(R.string.about_tagline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        val rows = listOf(
            Triple(R.string.about_source, "github.com/Dustin2/jarvis", "https://github.com/Dustin2/jarvis"),
            Triple(R.string.about_fonts, stringResource(R.string.about_fonts_hint), null),
        )
        rows.forEachIndexed { i, (title, hint, link) ->
            val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            val m = Modifier.inBlock(i, rows.lastIndex)
            if (link != null) {
                ListItem(onClick = { uri.openUri(link) }, shapes = blockRowShapes(), colors = colors, supportingContent = { Text(hint) }, modifier = m) { Text(stringResource(title)) }
            } else {
                ListItem(headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(hint) }, colors = colors, modifier = m)
            }
        }
    }
}

@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}

@Composable
private fun timeText(time: LocalTime): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return time.format(DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", appLocale()))
}


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
        else stringResource(R.string.auto_backup_last, last.toLocalDate().inlineLabel(today), last.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale()))),
    )
    if (state.lastFailure != null) Text(stringResource(R.string.auto_backup_problem), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = vm::backUpNow, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_up_now)) }
    OutlinedButton(onClick = onPickFolder, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_folder)) }
    TextButton(onClick = vm::disableAutoBackup, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.turn_off_auto_backup)) }
}

@Composable
private fun ModelSection(state: ModelState, vm: SettingsViewModel) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(settings: AppearanceSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    AppearancePreview()
    Choice(R.string.style, Style.entries, settings.style, { it.label }, vm::setStyle)
    Text(stringResource(settings.style.hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Choice(R.string.theme_mode, ThemeMode.entries, settings.mode, { it.label }, vm::setThemeMode)
    Text(stringResource(R.string.palette), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The wallpaper's own color, so that chip shows what it would look like too.
        val wallpaper = remember(context) { dynamicLightColorScheme(context).primary }
        Palette.entries.forEach { palette ->
            val selected = palette == settings.palette
            FilterChip(
                selected = selected,
                onClick = { vm.setPalette(palette) },
                label = { Text(stringResource(palette.label)) },
                // A dot in the palette's color, plus the check when picked: not color alone.
                leadingIcon = {
                    if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
                    else Box(Modifier.size(FilterChipDefaults.IconSize).background(if (palette == Palette.Wallpaper) wallpaper else palette.swatch, CircleShape))
                },
            )
        }
    }
}

/** A few sample tasks in the current colors: the whole app recolors live, so this shows the pick at a glance. */
@Composable
private fun AppearancePreview() {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.appearance_preview)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(description, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Row(
                Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(24.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(16.dp), tint = colors.onPrimary)
                }
                Text(stringResource(R.string.preview_task_done), color = colors.onPrimaryContainer, textDecoration = TextDecoration.LineThrough)
            }
            Row(
                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(24.dp).border(2.dp, colors.outline, CircleShape))
                Text(stringResource(R.string.preview_task_open), Modifier.weight(1f))
                Text(
                    stringResource(R.string.preview_project),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.background(colors.secondaryContainer, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Morning brief: on/off and the time. Turning it on asks for the notification permission if needed. */
// TimePicker is still marked experimental, but it is the official M3 picker.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefSection(settings: BriefSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    var notificationsOff by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationsOff = !granted }
    var picking by remember { mutableStateOf(false) }
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeText = timeText(settings.time)

    Text(stringResource(R.string.brief_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.fillMaxWidth().toggleable(settings.enabled, role = Role.Switch) { on ->
            vm.setBriefEnabled(on)
            if (on && notificationsOff) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.brief_on), Modifier.weight(1f))
        Switch(checked = settings.enabled, onCheckedChange = null)
    }
    if (settings.enabled) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.brief_time, timeText)) }
    }
    Row(
        Modifier.fillMaxWidth().toggleable(settings.weekly, role = Role.Switch) { on ->
            vm.setWeekly(on)
            if (on && notificationsOff) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.weekly_on), Modifier.weight(1f))
        Switch(checked = settings.weekly, onCheckedChange = null)
    }
    if ((settings.enabled || settings.weekly) && notificationsOff) {
        Text(stringResource(R.string.notifications_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (picking) {
        val state = rememberTimePickerState(settings.time.hour, settings.time.minute, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.pick_time)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    vm.setBriefTime(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
