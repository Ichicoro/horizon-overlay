package sh.zelda.htmlfeed.hub

import android.webkit.WebView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sh.zelda.htmlfeed.HubModule
import sh.zelda.htmlfeed.HubSettings

/**
 * The -1 screen: the enabled modules, in the order settings put them.
 *
 * [refreshKey] changes whenever the panel is reopened or pulled down, which is what tells the
 * modules holding data (contacts, calendar, weather) to go and look again. [onRefresh] bumps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    settings: HubSettings,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onWebView: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val modules = settings.enabledModules
    var isRefreshing by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<Result<WeatherReport>?>(null) }

    // The single owner of the weather fetch, and so of the refresh indicator: it restarts on
    // every key change, and clearing the flag when it returns is what stops the spinner. The
    // indicator therefore lasts as long as the request it stands for. Contacts and the calendar
    // reload off the same key, but they're local queries that finish long before this does.
    LaunchedEffect(settings.metricUnits, refreshKey) {
        val startedAt = System.currentTimeMillis()
        weather = WeatherRepository.loadCurrent(context, settings.metricUnits)
        if (isRefreshing) {
            // With no location permission - or a warm cache - this returns instantly, and an
            // indicator that vanishes on contact reads as a bug rather than a refresh.
            delay((MIN_INDICATOR_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
            isRefreshing = false
        }
    }

    // A page on its own gets the whole panel, with no card around it: that's the overlay this
    // app started as, and it's still worth having as a mode. No pull-to-refresh there - the
    // gesture belongs to the page, which scrolls itself.
    if (modules == listOf(HubModule.WEB)) {
        WebModule(
            url = settings.pageUrl,
            onWebView = onWebView,
            // A page is one opaque view: it can't hold its own content clear of the bars the
            // way the list below does, so it gets padded out of them instead.
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        )
        return
    }

    // The bars are the list's business, not the panel's. Padding the whole panel away from
    // them would put the list's top edge under the status bar, and a scrolled card would then
    // be cut off along that line in mid-air. The insets go on the list's contentPadding
    // instead: cards start and end clear of the bars, and scroll underneath them.
    val barInsets = WindowInsets.systemBars.asPaddingValues()
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            // Without this the 15-minute cache would hand back the same reading and the pull
            // would be theatre.
            WeatherRepository.invalidate()
            onRefresh()
        },
        state = pullState,
        // The box is edge to edge, so the indicator would come down behind the status bar.
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        HubList(
            modules = modules,
            settings = settings,
            refreshKey = refreshKey,
            weather = weather,
            onWebView = onWebView,
            barInsets = barInsets,
        )
    }
}

@Composable
private fun HubList(
    modules: List<HubModule>,
    settings: HubSettings,
    refreshKey: Int,
    weather: Result<WeatherReport>?,
    onWebView: (WebView?) -> Unit,
    barInsets: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 20.dp + barInsets.calculateTopPadding(),
            bottom = 20.dp + barInsets.calculateBottomPadding(),
        ),
    ) {
        if (modules.isEmpty()) {
            item { EmptyHub() }
        }
        itemsIndexed(modules, key = { _, module -> module.id }) { index, module ->
            // Segments of one grouped list: a run of adjacent cards shares a container, so only
            // the ends of a run get the large corner. The clock isn't a card, so it caps the run
            // above it and starts a new one below.
            val joinAbove = module.isSegment && modules.getOrNull(index - 1)?.isSegment == true
            val joinBelow = module.isSegment && modules.getOrNull(index + 1)?.isSegment == true
            val shape = SegmentShapes.of(joinAbove, joinBelow)
            val spacing = Modifier.padding(
                bottom = if (joinBelow) SegmentShapes.gap else SegmentShapes.sectionGap,
            )

            when (module) {
                HubModule.CLOCK -> ClockCard(
                    showDate = settings.clockShowDate,
                    modifier = Modifier.padding(start = 4.dp, bottom = SegmentShapes.sectionGap),
                )

                HubModule.CONTACTS -> ContactsCard(
                    limit = settings.contactsLimit,
                    refreshKey = refreshKey,
                    modifier = spacing,
                    shape = shape,
                )

                HubModule.WEATHER -> WeatherCard(
                    report = weather,
                    modifier = spacing,
                    shape = shape,
                )

                HubModule.NAVIGATION -> NavigationCard(
                    selection = settings.navigationSelection,
                    refreshKey = refreshKey,
                    modifier = spacing,
                    shape = shape,
                )

                HubModule.AGENDA -> AgendaCard(
                    days = settings.agendaDays,
                    refreshKey = refreshKey,
                    modifier = spacing,
                    shape = shape,
                )

                HubModule.WEB -> WebCard(
                    url = settings.pageUrl,
                    onWebView = onWebView,
                    modifier = spacing,
                    shape = shape,
                )
            }
        }
    }
}

/** Long enough for the indicator to read as a refresh rather than a flicker. */
private const val MIN_INDICATOR_MS = 450L

/** Whether a module draws as a card, and so takes part in the grouping. */
private val HubModule.isSegment: Boolean get() = this != HubModule.CLOCK

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
