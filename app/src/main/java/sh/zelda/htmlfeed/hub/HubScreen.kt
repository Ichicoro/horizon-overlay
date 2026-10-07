package sh.zelda.htmlfeed.hub

import android.webkit.WebView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sh.zelda.htmlfeed.HubModule
import sh.zelda.htmlfeed.HubSettings

/**
 * The -1 screen: the enabled modules, in the order settings put them.
 *
 * [refreshKey] changes whenever the panel is reopened, which is what tells the modules holding
 * data (contacts, calendar, weather) to go and look again.
 */
@Composable
fun HubScreen(
    settings: HubSettings,
    refreshKey: Int,
    onWebView: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modules = settings.enabledModules

    // A page on its own gets the whole panel, with no card around it: that's the overlay this
    // app started as, and it's still worth having as a mode.
    if (modules == listOf(HubModule.WEB)) {
        WebModule(
            url = settings.pageUrl,
            onWebView = onWebView,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        // The overlay keeps content off the status bar; the navigation bar is this list's
        // problem, since it only matters for whatever ends up at the bottom.
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    ) {
        if (modules.isEmpty()) {
            item { EmptyHub() }
        }
        items(modules, key = { it.id }) { module ->
            val spacing = Modifier.padding(bottom = 12.dp)
            when (module) {
                HubModule.CLOCK -> ClockCard(
                    showDate = settings.clockShowDate,
                    modifier = Modifier.padding(start = 4.dp, bottom = 20.dp),
                )

                HubModule.CONTACTS -> ContactsCard(
                    limit = settings.contactsLimit,
                    refreshKey = refreshKey,
                    modifier = spacing,
                )

                HubModule.WEATHER -> WeatherCard(
                    place = settings.weather,
                    metricUnits = settings.metricUnits,
                    refreshKey = refreshKey,
                    modifier = spacing,
                )

                HubModule.AGENDA -> AgendaCard(
                    days = settings.agendaDays,
                    refreshKey = refreshKey,
                    modifier = spacing,
                )

                HubModule.WEB -> WebCard(
                    url = settings.pageUrl,
                    onWebView = onWebView,
                    modifier = spacing,
                )
            }
        }
    }
}

@Composable
private fun EmptyHub() {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    HubCard(title = "Nothing here yet") {
        HubPlaceholder(
            message = "Every module is switched off. Turn some on and put them in the order you " +
                "want them.",
            actionLabel = "Open settings",
            onAction = { actions.launch(hubSettingsIntent(context)) },
        )
    }
}
