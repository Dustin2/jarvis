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
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import kotlinx.coroutines.flow.combine

/**
 * The first shopping list on the home screen: what is still to buy, checked off without opening the app
 * (it moves to the cart, as in Compras). The title and "+" open Compras on that list.
 */
// shortcut: always the first list; let the user pick one per widget if people keep several lists.
class ShoppingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val shopping = context.app.shopping
        val firstList = combine(shopping.lists, shopping.allItems) { lists, items ->
            lists.firstOrNull()?.let { list -> list to items.filter { it.listId == list.id && !it.checked } }
        }
        provideContent {
            // Collected inside the widget session: a change in the database redraws it while the session lives.
            val state by firstList.collectAsState(initial = null)
            GlanceTheme { state?.let { (list, toBuy) -> Content(list, toBuy) } }
        }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

private val ItemId = ActionParameters.Key<String>("itemId")

class BuyItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemId] ?: return
        context.app.shopping.setChecked(id, true)
        ShoppingWidget().update(context, glanceId)
    }
}

// CLEAR_TOP + SINGLE_TOP: an open app receives it in onNewIntent instead of stacking another copy.
private fun listIntent(context: Context, listId: String) = Intent(context, MainActivity::class.java)
    .putExtra(MainActivity.EXTRA_LIST_ID, listId)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
private fun Content(list: ShoppingList, toBuy: List<ShoppingItem>) {
    val context = LocalContext.current
    val open = actionStartActivity(listIntent(context, list.id))
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(28.dp).padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Text(list.name, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                Text(context.getString(R.string.to_buy, toBuy.size), style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
            SquareIconButton(imageProvider = ImageProvider(R.drawable.ic_add), contentDescription = context.getString(R.string.add_item_hint), onClick = open)
        }
        if (toBuy.isEmpty()) {
            Text(
                context.getString(R.string.widget_shopping_empty),
                style = TextStyle(fontSize = 15.sp, color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                items(toBuy, itemId = { it.id.hashCode().toLong() }) { item -> ItemLine(item) }
            }
        }
    }
}

@Composable
private fun ItemLine(item: ShoppingItem) {
    val context = LocalContext.current
    val label = context.getString(R.string.widget_mark_bought, item.name)
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<BuyItemAction>(actionParametersOf(ItemId to item.id)),
            // With the item's name: TalkBack reads the checkbox on its own.
            modifier = GlanceModifier.semantics { contentDescription = label },
        )
        Text(item.name, maxLines = 1, style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurface), modifier = GlanceModifier.defaultWeight())
        item.quantity?.let {
            Text(it, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.padding(start = 8.dp))
        }
    }
}
