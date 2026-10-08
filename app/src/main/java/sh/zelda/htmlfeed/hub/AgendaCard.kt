package sh.zelda.htmlfeed.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The next handful of calendar events, each one a tap away from the calendar app. */
@Composable
fun AgendaCard(
    days: Int,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentShapes.single,
) {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    val granted = CalendarRepository.hasPermission(context)
    val events by produceState(initialValue = emptyList<AgendaEvent>(), granted, days, refreshKey) {
        value = if (granted) CalendarRepository.upcoming(context, days) else emptyList()
    }

    HubCard(
        title = "Up next",
        modifier = modifier,
        shape = shape,
        actionLabel = if (granted) "Calendar" else null,
        onAction = { actions.launch(CalendarRepository.openCalendarIntent()) },
    ) {
        when {
            !granted -> HubPlaceholder(
                message = "Allow access to your calendar in HTML Feed's settings to see what's coming up.",
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            events.isEmpty() -> HubPlaceholder(
                message = "Nothing scheduled in the next ${if (days == 1) "day" else "$days days"}.",
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEach { event ->
                    AgendaRow(event) { actions.launch(event.viewIntent) }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(event: AgendaEvent, onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = event.detailLine(context, locale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
