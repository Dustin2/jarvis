package dev.dux.organizer.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dux.organizer.MainActivity
import dev.dux.organizer.R
import dev.dux.organizer.app
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.today.DayPlan
import dev.dux.organizer.today.planDay
import java.time.LocalDate

/**
 * "Mi día" on the home screen (design: docs/design/widget.md): overdue and today's tasks, check them off
 * without opening the app, tap one to open it, "+" to capture. Colors follow the wallpaper (GlanceTheme).
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = context.app.tasks.open
        provideContent {
            // Collected inside the widget session: a change in the database redraws it while the session lives.
            val open by tasks.collectAsState(initial = null)
            GlanceTheme { open?.let { Content(planDay(it, LocalDate.now())) } }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val TaskId = ActionParameters.Key<String>("taskId")

/** Checking a task in the widget completes it, with the same rules as in the app (recurring tasks create the next one). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskId] ?: return
        context.app.tasks.setStatus(id, TaskStatus.DONE)
        TodayWidget().update(context, glanceId)
    }
}

// CLEAR_TOP + SINGLE_TOP: an open app receives it in onNewIntent instead of stacking another copy.
private fun appIntent(context: Context) = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
private fun Content(plan: DayPlan) {
    val context = LocalContext.current
    val tasks = plan.overdue + plan.today
    val summary = context.resources.getQuantityString(R.plurals.open_count, plan.openCount, plan.openCount) + " · " +
        context.resources.getQuantityString(R.plurals.overdue_count, plan.overdue.size, plan.overdue.size)

    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(28.dp).padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(context)))) {
                Text(context.getString(R.string.tab_today), style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                Text(summary, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
            SquareIconButton(
                imageProvider = ImageProvider(R.drawable.ic_add),
                contentDescription = context.getString(R.string.capture),
                onClick = actionStartActivity(appIntent(context).putExtra(MainActivity.EXTRA_CAPTURE, true)),
            )
        }
        if (tasks.isEmpty()) {
            Text(
                context.getString(R.string.empty_today),
                style = TextStyle(fontSize = 15.sp, color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task -> TaskLine(task, overdue = task in plan.overdue) }
            }
        }
    }
}

@Composable
private fun TaskLine(task: Task, overdue: Boolean) {
    val context = LocalContext.current
    val checkLabel = context.getString(R.string.complete_task, task.title)
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskId to task.id)),
            // With the task's name: TalkBack reads the checkbox on its own.
            modifier = GlanceModifier.semantics { contentDescription = checkLabel },
        )
        Text(
            task.title,
            maxLines = 1,
            style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(context).putExtra(MainActivity.EXTRA_TASK_ID, task.id))),
        )
        Text(
            context.getString(if (overdue) R.string.widget_overdue else R.string.today),
            style = TextStyle(fontSize = 13.sp, color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.padding(start = 8.dp),
        )
    }
}
