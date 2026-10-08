package sh.zelda.htmlfeed

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import sh.zelda.htmlfeed.hub.NavDestination
import sh.zelda.htmlfeed.hub.NavigationRepository
import sh.zelda.htmlfeed.hub.CalendarRepository
import sh.zelda.htmlfeed.hub.ContactsRepository
import sh.zelda.htmlfeed.hub.LocationRepository

/**
 * Where the panel gets arranged: every module in the order it will appear, with its switch, its
 * options, and whatever permission it still needs.
 *
 * Changes are written as they're made - there's no save button for the layout, because the
 * overlay re-reads settings every time it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var layout by remember { mutableStateOf(Settings.layout(context)) }
    var expanded by remember { mutableStateOf<HubModule?>(null) }

    fun update(newLayout: List<HubModuleState>) {
        layout = newLayout
        Settings.setLayout(context, newLayout)
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Hub") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "In your launcher's settings (Lawnchair, Neo Launcher, ...) pick " +
                    "\"HTML Feed\" as the feed provider, then swipe right from the first " +
                    "home screen.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OverlayPermissionNotice()

            Text(
                text = "Modules",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            // Pinned modules hold the top, so the movable range starts below them.
            // coerced: with nothing movable the range below would start at -1.
            val firstMovable = layout.indexOfFirst { !it.module.pinned }.coerceAtLeast(0)

            layout.forEachIndexed { index, state ->
                ModuleRow(
                    state = state,
                    isFirst = index <= firstMovable,
                    isLast = index == layout.lastIndex,
                    isExpanded = expanded == state.module,
                    onToggleExpanded = {
                        expanded = if (expanded == state.module) null else state.module
                    },
                    onSetEnabled = { enabled ->
                        update(layout.toMutableList().also { it[index] = state.copy(enabled = enabled) })
                    },
                    onMove = { offset ->
                        val target = index + offset
                        if (target in firstMovable..layout.lastIndex) {
                            update(layout.toMutableList().also {
                                it[index] = it[target]
                                it[target] = state
                            })
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * The one permission the panel can't work without, and the one that reads worst in a settings
 * screen - so it says what it's actually for.
 *
 * Starting an activity from the -1 panel is a background activity start: the panel window hangs
 * off the launcher's activity token, so the system attributes it to the launcher and this app
 * looks like it has nothing on screen. "Display over other apps" is the only exemption left,
 * and without it every tap is dropped with BAL_BLOCK - the panel shuts and nothing opens.
 */
@Composable
private fun OverlayPermissionNotice() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    // Returning from the settings screen is the signal to look again.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { granted = android.provider.Settings.canDrawOverlays(context) }

    if (granted) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Taps can't open anything yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "Android blocks apps from opening other apps when they have nothing on " +
                    "screen, and the -1 panel doesn't count. Allowing \"display over other " +
                    "apps\" lifts that block. Nothing gets drawn over anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = {
                    launcher.launch(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Allow") }
        }
    }
}

@Composable
private fun ModuleRow(
    state: HubModuleState,
    isFirst: Boolean,
    isLast: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.enabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            // Arrows rather than drag: they're reachable one-handed and don't fight the
            // scrolling of the page they sit on.
            Column {
                MoveButton("▲", enabled = !isFirst) { onMove(-1) }
                MoveButton("▼", enabled = !isLast) { onMove(+1) }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = state.module.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.module.pinned) {
                        "Always at the top · ${state.module.summary}"
                    } else {
                        state.module.summary
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onSetEnabled)
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                when (state.module) {
                    HubModule.CLOCK -> ClockOptions()
                    HubModule.CONTACTS -> ContactsOptions()
                    HubModule.WEATHER -> WeatherOptions()
                    HubModule.NAVIGATION -> NavigationOptions()
                    HubModule.AGENDA -> AgendaOptions()
                    HubModule.WEB -> WebOptions()
                }
            }
        }
    }
}

@Composable
private fun MoveButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp),
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun ClockOptions() {
    val context = LocalContext.current
    var showDate by remember { mutableStateOf(Settings.snapshot(context).clockShowDate) }
    SettingSwitch(
        label = "Show the date",
        checked = showDate,
        onCheckedChange = {
            showDate = it
            Settings.setClockShowDate(context, it)
        },
    )
}

@Composable
private fun ContactsOptions() {
    val context = LocalContext.current
    var limit by remember { mutableIntStateOf(Settings.snapshot(context).contactsLimit) }

    PermissionNotice(
        rationale = "Needed to list the contacts you've starred.",
        permission = ContactsRepository.PERMISSION,
    )
    Stepper(
        label = "Favorites shown",
        value = limit,
        range = 2..24,
        onChange = {
            limit = it
            Settings.setContactsLimit(context, it)
        },
    )
    Text(
        text = "Tap someone to open their contact, hold to call.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Which addresses the card shows.
 *
 * Everything is ticked until the first time something is unticked: before that the stored
 * selection is null, which means "all of them" and keeps a newly saved address appearing without
 * a trip through here.
 */
@Composable
private fun NavigationOptions() {
    val context = LocalContext.current
    var selection by remember { mutableStateOf(Settings.snapshot(context).navigationSelection) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val granted = remember(permissionTick) { NavigationRepository.hasPermission(context) }
    val destinations by produceState(emptyList<NavDestination>(), granted) {
        value = if (granted) NavigationRepository.destinations(context) else emptyList()
    }

    PermissionNotice(
        rationale = "Needed to read the addresses saved on your contacts.",
        permission = NavigationRepository.PERMISSION,
        onResult = { permissionTick++ },
    )

    if (!granted) return
    if (destinations.isEmpty()) {
        Text(
            text = "None of your contacts has an address saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    fun set(newSelection: Set<String>?) {
        selection = newSelection
        Settings.setNavigationSelection(context, newSelection)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (selection == null) "Showing all" else "Showing ${selection?.size ?: 0}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selection != null) {
            TextButton(onClick = { set(null) }) { Text("Select all") }
        }
    }

    destinations.forEach { destination ->
        val checked = selection?.contains(destination.key) ?: true
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // The first untick turns "all" into a real set, so it has to start from
                    // everything rather than from nothing.
                    val current = selection ?: destinations.map { it.key }.toSet()
                    set(if (checked) current - destination.key else current + destination.key)
                },
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                Text(
                    text = "${destination.name} · ${destination.label}",
                    style = MaterialTheme.typography.bodyLarge,
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
}

@Composable
private fun AgendaOptions() {
    val context = LocalContext.current
    var days by remember { mutableIntStateOf(Settings.snapshot(context).agendaDays) }

    PermissionNotice(
        rationale = "Needed to read your upcoming events.",
        permission = CalendarRepository.PERMISSION,
    )
    Stepper(
        label = "Days ahead",
        value = days,
        range = 1..31,
        onChange = {
            days = it
            Settings.setAgendaDays(context, it)
        },
    )
}

@Composable
private fun WeatherOptions() {
    val context = LocalContext.current
    var metric by remember { mutableStateOf(Settings.snapshot(context).metricUnits) }

    PermissionNotice(
        rationale = "Needed to show the weather where you are. There's no saved city: the " +
            "panel always reports your current location.",
        permission = LocationRepository.PERMISSION,
    )
    BackgroundLocationNotice()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = metric,
            onClick = {
                metric = true
                Settings.setMetricUnits(context, true)
            },
            label = { Text("°C") },
        )
        FilterChip(
            selected = !metric,
            onClick = {
                metric = false
                Settings.setMetricUnits(context, false)
            },
            label = { Text("°F") },
        )
    }
    Text(
        text = "Forecasts come from Open-Meteo, which needs no account and no key.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun WebOptions() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var url by remember { mutableStateOf(Settings.snapshot(context).pageUrl) }

    fun save(value: String) {
        val normalized = Settings.normalize(value)
        Settings.setPageUrl(context, normalized)
        url = normalized
        keyboard?.hide()
    }

    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Page to show") },
        placeholder = { Text(Settings.DEFAULT_URL) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { save(url) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = { save(Settings.DEFAULT_URL) }) { Text("Reset") }
        Button(
            onClick = { save(url) },
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Save") }
    }
    Text(
        text = "A local page works too: put it in the app's assets and use " +
            "file:///android_asset/index.html. With every other module switched off, the page " +
            "gets the whole panel.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Asks for a runtime permission, and says nothing at all once it's been granted. */
/**
 * The second half of the location grant, and the one that decides whether the weather card
 * works away from home.
 *
 * "While using the app" is no use to the panel: it's drawn by a service the launcher binds, so
 * the system never counts this app as on screen there and refuses every read. Granted only from
 * the system settings page - since Android 11 a runtime request for it is denied without even
 * showing a dialog - so this sends the user there rather than pretending to ask.
 */
@Composable
private fun BackgroundLocationNotice() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocationRepository.hasBackgroundPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { granted = LocationRepository.hasBackgroundPermission(context) }

    // Nothing to say until the coarse grant is in: its own notice is asking for that already.
    if (granted || !LocationRepository.hasPermission(context)) return
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = "Location is set to \"while using the app\", and the -1 panel doesn't count " +
                "as using it - the launcher draws it, so Android treats this app as closed and " +
                "refuses to say where you are. Until it's set to \"all the time\", the card " +
                "shows the last place picked up while this screen was open.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                launcher.launch(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri(),
                    ),
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Open app settings") }
    }
}

@Composable
private fun PermissionNotice(
    rationale: String,
    permission: String,
    onResult: () -> Unit = {},
) {
    val context = LocalContext.current
    // The result callback is what tells us to look again; the check itself is cheap.
    var attempts by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        attempts++
        onResult()
    }
    val granted = remember(attempts) { hasPermission(context, permission) }

    if (granted) return
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { launcher.launch(permission) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Allow") }
    }
}

private fun hasPermission(context: Context, permission: String) = when (permission) {
    // Navigation reads contacts too, so its permission is the same constant.
    ContactsRepository.PERMISSION -> ContactsRepository.hasPermission(context)
    CalendarRepository.PERMISSION -> CalendarRepository.hasPermission(context)
    LocationRepository.PERMISSION -> LocationRepository.hasPermission(context)
    else -> true
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A number with a minus and a plus: fewer taps than a slider for a count this small. */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        MoveButton("−", enabled = value > range.first) { onChange(value - 1) }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        MoveButton("+", enabled = value < range.last) { onChange(value + 1) }
    }
}
