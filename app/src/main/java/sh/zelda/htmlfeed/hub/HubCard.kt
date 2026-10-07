package sh.zelda.htmlfeed.hub

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.zelda.htmlfeed.MainActivity

/**
 * How a module asks for an app to be opened.
 *
 * The overlay lives in a Service and has to close the panel before it hands off, so modules get
 * this instead of reaching for a context of their own.
 */
val LocalHubActions = compositionLocalOf<HubActions> { HubActions {} }

fun interface HubActions {
    fun launch(intent: Intent)
}

/**
 * Every module sits in one of these. They're segments of a single grouped list rather than
 * free-floating cards: [shape] rounds the outside of a run and squares off where two segments
 * meet, which is what makes a column of them read as one container.
 */
@Composable
fun HubCard(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentShapes.single,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        // Filled card, default container role; everything tonal comes from the wallpaper via
        // dynamic color.
        shape = shape,
        colors = CardDefaults.cardColors(),
    ) {
        // Only the vertical inset is the card's to give. The horizontal one belongs to the
        // header and to [contentPadding], so a module whose content should run to the card's
        // edges - a scrolling row - can decline it and pad its own scroll area instead.
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // The action's own padding supplies the rest of its inset, which lands its
                    // label the same 16dp off the edge as the title.
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                if (actionLabel != null && onAction != null) {
                    // A button's 40dp minimum would make this header half again as tall as
                    // every other card's and push the content down with it.
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.height(ACTION_HEIGHT),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(actionLabel) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(contentPadding)) { content() }
        }
    }
}

/** Keeps a card's header the same height whether or not it carries an action. */
private val ACTION_HEIGHT = 24.dp

/** Shared by the modules that need a permission, or have nothing to show yet. */
@Composable
fun HubPlaceholder(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
            ) { Text(actionLabel) }
        }
    }
}

/**
 * The corner sizes a grouped list is built from: a large radius on the outside of a run, a tight
 * one where two segments meet, and a hairline gap so the seam reads as a division rather than a
 * border.
 */
object SegmentShapes {
    val outer: Dp = 28.dp
    val inner: Dp = 4.dp

    /** Between two segments of the same group. */
    val gap: Dp = 2.dp

    /** Between groups, or either side of something that isn't a segment. */
    val sectionGap: Dp = 20.dp

    val single: Shape = RoundedCornerShape(outer)

    /** [joinAbove]/[joinBelow] say whether this segment has a neighbour to square off against. */
    fun of(joinAbove: Boolean, joinBelow: Boolean): Shape = RoundedCornerShape(
        topStart = if (joinAbove) inner else outer,
        topEnd = if (joinAbove) inner else outer,
        bottomStart = if (joinBelow) inner else outer,
        bottomEnd = if (joinBelow) inner else outer,
    )
}

/** Opens this app's own settings screen, where permissions and module options live. */
fun hubSettingsIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
