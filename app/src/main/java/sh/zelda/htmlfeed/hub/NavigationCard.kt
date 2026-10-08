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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Places rather than people: every address on every contact, one tap from directions.
 *
 * Laid out as rows instead of the favorites row's circles, because which address matters here -
 * someone's home and their work are two different destinations and have to be told apart.
 */
@Composable
fun NavigationCard(
    selection: Set<String>?,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentShapes.single,
) {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    val granted = NavigationRepository.hasPermission(context)
    val destinations by produceState(emptyList<NavDestination>(), granted, selection, refreshKey) {
        value = if (granted) {
            NavigationRepository.filter(NavigationRepository.destinations(context), selection)
        } else {
            emptyList()
        }
    }

    HubCard(title = "Quick navigation", modifier = modifier, shape = shape) {
        when {
            !granted -> HubPlaceholder(
                message = "Allow access to contacts in HTML Feed's settings to navigate to the " +
                    "people you've saved an address for.",
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            destinations.isEmpty() -> HubPlaceholder(
                message = if (selection == null) {
                    "None of your contacts has an address saved."
                } else {
                    "No destinations picked. Choose some in HTML Feed's settings."
                },
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                destinations.forEach { destination ->
                    DestinationRow(destination) {
                        actions.launch(destination.navigationIntent(context))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(destination: NavDestination, onClick: () -> Unit) {
    val (container, onContainer) = avatarColors(destination.name)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(container, CircleShape),
        ) {
            Text(
                text = destination.initials,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = onContainer,
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "${destination.name} · ${destination.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = destination.shortAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
