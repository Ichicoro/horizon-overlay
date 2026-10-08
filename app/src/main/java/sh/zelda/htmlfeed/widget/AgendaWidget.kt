package sh.zelda.htmlfeed.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.lazy.itemsIndexed
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
import androidx.glance.text.Text
import sh.zelda.htmlfeed.Settings
import sh.zelda.htmlfeed.hub.AgendaEvent
import sh.zelda.htmlfeed.hub.CalendarRepository
import sh.zelda.htmlfeed.hub.detailLine

/** The agenda card as a home-screen widget: the next few events, each a tap from the calendar. */
class AgendaWidget : GlanceAppWidget() {
    /**
     * One composition per actual size rather than a handful of buckets, so the list grows by a
     * row at a time as the widget is resized instead of jumping between layouts.
     */
    override val sizeMode = SizeMode.Exact

    /**
     * The query runs here, not in the composition: Glance has no [androidx.compose.runtime.produceState]
     * to suspend in, and this function is already a coroutine the receiver re-runs on every update.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val days = Settings.snapshot(context).agendaDays
        val granted = CalendarRepository.hasPermission(context)
        val events = if (granted) CalendarRepository.upcoming(context, days) else emptyList()

        provideContent {
            GlanceTheme {
                HubWidgetSurface(
                    title = "Up next",
                    actionLabel = if (granted) "Calendar" else null,
                    action = actionStartActivity(CalendarRepository.openCalendarIntent()),
                ) {
                    when {
                        !granted -> WidgetPlaceholder(
                            "Allow access to your calendar to see what's coming up.",
                        )

                        events.isEmpty() -> WidgetPlaceholder(
                            "Nothing scheduled in the next " +
                                if (days == 1) "day." else "$days days.",
                        )

                        // Scrolls rather than truncating, so a short widget still reaches
                        // everything the panel's card would have shown.
                        // Indexed, so the list ids are unique by construction: a repeating
                        // event is several instances sharing one event id, and two rows of a
                        // RemoteViews collection answering to the same id send taps astray.
                        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            itemsIndexed(events) { _, event -> AgendaWidgetRow(event) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaWidgetRow(event: AgendaEvent) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(actionStartActivity(event.viewIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The calendar's own colour where the event has one, the wallpaper's where it doesn't -
        // same as the card.
        val dot = GlanceModifier.size(8.dp).cornerRadius(4.dp)
        Box(
            modifier = event.color
                ?.let { dot.background(Color(it)) }
                ?: dot.background(GlanceTheme.colors.primary),
            content = {},
        )
        Column(modifier = GlanceModifier.padding(start = 10.dp).defaultWeight()) {
            Text(text = event.title, style = WidgetText.primary, maxLines = 1)
            Text(text = event.detailLine(context, locale), style = WidgetText.secondary, maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.width(2.dp))
    }
}
