package sh.zelda.htmlfeed.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen half of the hub: one widget per module that can stand on its own.
 *
 * The clock and the weather are deliberately absent. A clock widget is a solved problem that
 * every launcher already ships, and the weather module forecasts for wherever the phone is, which
 * needs a background location read the panel only gets because the launcher is holding it open -
 * a widget has no such cover, and a stale reading is worse than no card at all. The web page has
 * no widget either: a RemoteViews can only be built from a fixed set of views and WebView isn't
 * one of them, so there's no way to put a page on the home screen.
 */
object HubWidgets {
    private val ALL get() = listOf(ContactsWidget(), NavigationWidget(), AgendaWidget())

    /** Outlives any one caller's scope - a preference write has none of its own. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Ask the placed widgets to rebuild, and return without waiting for them.
     *
     * It has to be [updateAll] rather than an `APPWIDGET_UPDATE` broadcast at the provider, which
     * is the older idiom: that action is a protected broadcast, so the system is the only thing
     * allowed to send it. A widget nobody has placed costs nothing here.
     */
    fun refresh(context: Context) {
        scope.launch { ALL.forEach { it.updateAll(context) } }
    }
}

class ContactsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContactsWidget()
}

class NavigationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NavigationWidget()
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()

    /**
     * An edit in the calendar changes the list, and the clock being set changes every "Today" and
     * "Tomorrow" in it. Neither arrives as an app widget update, so they're turned into one.
     *
     * The day rolling over on its own isn't here: `ACTION_DATE_CHANGED` isn't one of the implicit
     * broadcasts a manifest receiver may still register for, so midnight is left to the half-hour
     * poll in the provider's `updatePeriodMillis`.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in REFRESH_ACTIONS) {
            // goAsync, because the update is a coroutine and a receiver that has returned is a
            // receiver whose process the system is free to kill.
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    glanceAppWidget.updateAll(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    private companion object {
        val REFRESH_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_PROVIDER_CHANGED,
        )
    }
}
