package dev.dux.organizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.composed
import dev.dux.organizer.ui.theme.LocalStyle
import dev.dux.organizer.ui.theme.Style

/**
 * One row of a list, drawn the way the chosen style draws lists. Calm: rows of one rounded block, where only the
 * first row rounds its top corners and only the last its bottom ones (Mi día sections, Compras lists).
 * Energy: every row is its own rounded card with a gap. The row paints its color itself
 * (e.g. ListItem containerColor = surfaceContainer).
 */
fun Modifier.inBlock(index: Int, lastIndex: Int): Modifier = composed {
    if (LocalStyle.current == Style.Energy) {
        padding(horizontal = 16.dp, vertical = 4.dp).clip(MaterialTheme.shapes.large)
    } else {
        val top = if (index == 0) 24.dp else 0.dp
        val bottom = if (index == lastIndex) 24.dp else 0.dp
        padding(horizontal = 16.dp).clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
    }
}

/**
 * Shapes for a ListItem inside a block: square in every state. Expressive list items have slightly rounded
 * corners of their own, which showed as thin seams between rows; the block's clip does the rounding.
 */
@Composable
fun blockRowShapes(): ListItemShapes = ListItemDefaults.shapes(
    shape = RectangleShape,
    selectedShape = RectangleShape,
    pressedShape = RectangleShape,
    focusedShape = RectangleShape,
    hoveredShape = RectangleShape,
    draggedShape = RectangleShape,
)
