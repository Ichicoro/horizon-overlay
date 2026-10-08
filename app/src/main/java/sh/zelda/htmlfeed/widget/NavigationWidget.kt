package sh.zelda.htmlfeed.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import sh.zelda.htmlfeed.Settings
import sh.zelda.htmlfeed.hub.NavDestination
import sh.zelda.htmlfeed.hub.NavigationRepository

/**
 * Quick navigation as a home-screen widget: the addresses saved on your contacts, one tap from
 * directions.
 *
 * Rows rather than the contacts widget's faces, for the same reason the panel's card uses them -
 * someone's home and their work are two destinations and telling them apart is the whole point.
 */
class NavigationWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val selection = Settings.snapshot(context).navigationSelection
        val granted = NavigationRepository.hasPermission(context)
        val destinations = if (granted) {
            NavigationRepository.filter(NavigationRepository.destinations(context), selection)
        } else {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                HubWidgetSurface(title = "Quick navigation") {
                    when {
                        !granted -> WidgetPlaceholder(
                            "Allow access to contacts to navigate to the people you've saved an " +
                                "address for.",
                        )

                        destinations.isEmpty() -> WidgetPlaceholder(
                            if (selection == null) {
                                "None of your contacts has an address saved."
                            } else {
                                "No destinations picked. Choose some in HTML Feed's settings."
                            },
                        )

                        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            // The lookup key and label, which is what settings keys a selection
                            // on too, so a row keeps its identity across a contacts sync.
                            items(destinations, itemId = { it.key.hashCode().toLong() }) {
                                DestinationWidgetRow(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationWidgetRow(destination: NavDestination) {
    val context = LocalContext.current
    val (container, onContainer) = widgetAvatarColors(destination.name)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(actionStartActivity(destination.navigationIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(INITIALS)
                .cornerRadius(INITIALS / 2)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = destination.initials,
                style = WidgetText.secondary.copy(
                    color = onContainer,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Column(modifier = GlanceModifier.padding(start = 10.dp).defaultWeight()) {
            Text(
                text = "${destination.name} · ${destination.label}",
                style = WidgetText.primary,
                maxLines = 1,
            )
            Text(text = destination.shortAddress, style = WidgetText.secondary, maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.width(2.dp))
    }
}

/** Same circle as the panel's rows, which sit a shade smaller than the favorites' faces. */
private val INITIALS = 40.dp
