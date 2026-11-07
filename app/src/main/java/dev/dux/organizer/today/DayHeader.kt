package dev.dux.organizer.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dux.organizer.R
import dev.dux.organizer.ui.theme.LocalStyle
import dev.dux.organizer.ui.theme.Style
import java.time.LocalTime

/** Greeting and today's progress, drawn in the chosen style. [total] counts what was already done today. */
@Composable
fun DayHeader(done: Int, total: Int, overdue: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        if (total == 0) 0f else done.toFloat() / total,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    )
    val greeting = stringResource(greetingFor(LocalTime.now()))
    val counted = pluralStringResource(R.plurals.progress_done, total, done, total)
    val left = total - done
    val m = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    when (LocalStyle.current) {
        Style.Calm -> Calm(greeting, done, total, left, progress, counted, m)
        Style.Energy -> Energy(greeting, done, total, left, progress, counted, m)
        Style.Night -> Night(greeting, total, left, progress, counted, m)
    }
    if (overdue > 0) {
        Text(
            pluralStringResource(R.plurals.overdue_count, overdue, overdue),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

internal fun greetingFor(now: LocalTime) = when (now.hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..18 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

/** Calm: a light, large greeting and a thin ring; few words, lots of air. */
@Composable
private fun Calm(greeting: String, done: Int, total: Int, left: Int, progress: Float, counted: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(greeting, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            if (total > 0) {
                Box(Modifier.size(72.dp).clearAndSetSemantics { contentDescription = counted }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text("$done/$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            when { total == 0 -> stringResource(R.string.day_empty); left == 0 -> stringResource(R.string.day_done_calm); else -> pluralStringResource(R.plurals.day_left_calm, left, left) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Energy: a bold colored card with the big count and a wavy bar that springs forward. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Energy(greeting: String, done: Int, total: Int, left: Int, progress: Float, counted: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primary, contentColor = colors.onPrimary, modifier = modifier) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (total > 0) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics { contentDescription = counted }) {
                    Text("$done", fontSize = 60.sp, lineHeight = 60.sp, fontWeight = FontWeight.ExtraBold)
                    Text(pluralStringResource(R.plurals.of_total_done, total, total), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                    color = colors.tertiaryContainer,
                    trackColor = colors.onPrimary.copy(alpha = 0.28f),
                )
            }
            Text(
                when {
                    total == 0 -> stringResource(R.string.day_empty)
                    left == 0 -> stringResource(R.string.day_done_energy)
                    done * 2 >= total -> stringResource(R.string.day_half_energy)
                    else -> stringResource(R.string.day_start_energy)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Night: a breathing orb speaks the day in one sentence; the progress glows segment by segment. */
@Composable
private fun Night(greeting: String, total: Int, left: Int, progress: Float, counted: String, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val breath by rememberInfiniteTransition(label = "orb").animateFloat(1f, 1.07f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "breath")
    Column(modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).scale(breath).border(2.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.dp).background(accent, CircleShape))
            }
            val sentence = when { total == 0 -> stringResource(R.string.day_empty); left == 0 -> stringResource(R.string.day_done_night); else -> pluralStringResource(R.plurals.day_left_night, left, left) }
            Text("$greeting. $sentence", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
        }
        if (total > 0) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = counted }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Up to 10 segments: with more, each would be too thin to read.
                val segments = minOf(total, 10)
                repeat(segments) { i ->
                    val on = i < (progress * segments + 0.01f).toInt()
                    val color by animateColorAsState(if (on) accent else MaterialTheme.colorScheme.surfaceContainerHighest, tween(400))
                    Box(
                        Modifier.weight(1f).height(6.dp)
                            .then(if (on) Modifier.shadow(8.dp, RoundedCornerShape(3.dp), ambientColor = accent, spotColor = accent) else Modifier)
                            .background(color, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}
