package dev.dux.organizer.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.dux.organizer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** A newer release on GitHub with its APK. */
data class Update(val version: String, val apkUrl: String)

private const val LATEST_RELEASE = "https://api.github.com/repos/Dustin2/jarvis/releases/latest"
private const val DISMISSED_DAY = "dismissed_day"

/** "v0.21.0" is newer than "0.20.0"; a part that is not a number counts as 0. */
internal fun isNewer(tag: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(tag)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** GitHub's "latest release" JSON → the update, or null if it is not newer or has no APK attached. */
internal fun parseLatest(json: String, current: String): Update? {
    val release = JSONObject(json)
    val tag = release.optString("tag_name")
    if (!isNewer(tag, current)) return null
    val assets = release.optJSONArray("assets") ?: return null
    val apk = (0 until assets.length()).map(assets::getJSONObject).firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
    return Update(tag.removePrefix("v"), apk.getString("browser_download_url"))
}

/** Null on any failure (no network, rate limit): an update check must never get in the way. */
private suspend fun latestUpdate(current: String): Update? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else parseLatest(connection.inputStream.bufferedReader().use { it.readText() }, current)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/**
 * On opening the app, offers the newest GitHub release. "Later" hides it until tomorrow.
 * shortcut: the browser downloads the APK and the user installs it; switch to PackageInstaller if
 * that becomes a hassle. Skipped in Jarvis Dev: device tests must not see the dialog.
 */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("updates", Context.MODE_PRIVATE) }
    var update by remember { mutableStateOf<Update?>(null) }
    LaunchedEffect(Unit) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return@LaunchedEffect
        if (prefs.getLong(DISMISSED_DAY, -1) == LocalDate.now().toEpochDay()) return@LaunchedEffect
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return@LaunchedEffect
        update = latestUpdate(current)
    }
    val shown = update ?: return
    val later = {
        prefs.edit { putLong(DISMISSED_DAY, LocalDate.now().toEpochDay()) }
        update = null
    }
    AlertDialog(
        onDismissRequest = later,
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_body, shown.version)) },
        confirmButton = {
            TextButton(onClick = {
                update = null
                context.startActivity(Intent(Intent.ACTION_VIEW, shown.apkUrl.toUri()))
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = later) { Text(stringResource(R.string.update_later)) } },
    )
}
