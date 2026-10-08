package sh.zelda.htmlfeed

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import sh.zelda.htmlfeed.hub.NavDestination
import sh.zelda.htmlfeed.hub.WeatherPlace

/**
 * Everything the hub remembers between runs: which modules are on, what order they sit in, and
 * each module's own handful of options.
 *
 * Reads go through [snapshot] so the overlay picks the whole configuration up in one pass and the
 * composables below it never touch a [Context].
 */
object Settings {
    const val DEFAULT_URL = "https://google.com"

    /** All modules, in the order a fresh install gets them. The web page is off until asked for. */
    private val DEFAULT_LAYOUT = listOf(
        HubModuleState(HubModule.CLOCK, enabled = true),
        HubModuleState(HubModule.AGENDA, enabled = true),
        HubModuleState(HubModule.WEATHER, enabled = true),
        HubModuleState(HubModule.CONTACTS, enabled = true),
        HubModuleState(HubModule.NAVIGATION, enabled = true),
        HubModuleState(HubModule.WEB, enabled = false),
    )

    private const val PREFS = "htmlfeed"
    private const val KEY_LAYOUT = "module_layout"
    private const val KEY_URL = "page_url"
    private const val KEY_WEATHER_METRIC = "weather_metric"
    private const val KEY_LAST_PLACE = "weather_place"
    private const val KEY_LAST_LAT = "weather_lat"
    private const val KEY_LAST_LON = "weather_lon"
    private const val KEY_AGENDA_DAYS = "agenda_days"
    private const val KEY_CONTACTS_LIMIT = "contacts_limit"
    private const val KEY_CLOCK_SHOW_DATE = "clock_show_date"
    private const val KEY_NAV_SELECTION = "navigation_selection"

    fun snapshot(context: Context): HubSettings {
        val prefs = prefs(context)
        return HubSettings(
            layout = layout(context),
            pageUrl = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
            metricUnits = prefs.getBoolean(KEY_WEATHER_METRIC, true),
            agendaDays = prefs.getInt(KEY_AGENDA_DAYS, 7),
            contactsLimit = prefs.getInt(KEY_CONTACTS_LIMIT, 8),
            clockShowDate = prefs.getBoolean(KEY_CLOCK_SHOW_DATE, true),
            navigationSelection = prefs.getStringSet(KEY_NAV_SELECTION, null),
        )
    }

    /**
     * The stored order, reconciled with the modules this build knows about: anything the stored
     * layout has never heard of lands at the end, switched off, so an update never silently
     * rearranges the panel.
     */
    fun layout(context: Context): List<HubModuleState> {
        val stored = prefs(context).getString(KEY_LAYOUT, null) ?: return DEFAULT_LAYOUT
        val parsed = stored.split(',').mapNotNull { entry ->
            val id = entry.substringBefore(':')
            HubModule.byId(id)?.let { HubModuleState(it, entry.substringAfter(':', "1") == "1") }
        }.distinctBy { it.module }
        val missing = HubModule.entries
            .filterNot { module -> parsed.any { it.module == module } }
            .map { HubModuleState(it, enabled = false) }
        return pinnedFirst((parsed + missing).ifEmpty { DEFAULT_LAYOUT })
    }

    fun setLayout(context: Context, layout: List<HubModuleState>) {
        val encoded = pinnedFirst(layout)
            .joinToString(",") { "${it.module.id}:${if (it.enabled) 1 else 0}" }
        prefs(context).edit { putString(KEY_LAYOUT, encoded) }
    }

    /**
     * Pinned modules are held at the top whatever the stored order says, so a layout written by
     * an older build - or a hand-edited one - can't strand the clock in the middle.
     * [sortedBy] is stable, so everything else keeps the order it was given.
     */
    private fun pinnedFirst(layout: List<HubModuleState>): List<HubModuleState> =
        layout.sortedBy { !it.module.pinned }

    fun setPageUrl(context: Context, url: String) {
        prefs(context).edit { putString(KEY_URL, url) }
    }

    /**
     * The last fix the app actually got, kept so the panel has somewhere to forecast for when
     * the system refuses it a live one. Not a user setting - nothing in the UI writes it.
     */
    fun lastKnownPlace(context: Context): WeatherPlace? {
        val prefs = prefs(context)
        val lat = prefs.getFloat(KEY_LAST_LAT, Float.NaN)
        val lon = prefs.getFloat(KEY_LAST_LON, Float.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return WeatherPlace(
            name = prefs.getString(KEY_LAST_PLACE, null)?.takeIf { it.isNotBlank() }
                ?: "Current location",
            latitude = lat.toDouble(),
            longitude = lon.toDouble(),
        )
    }

    fun setLastKnownPlace(context: Context, place: WeatherPlace) {
        prefs(context).edit {
            putString(KEY_LAST_PLACE, place.name)
            putFloat(KEY_LAST_LAT, place.latitude.toFloat())
            putFloat(KEY_LAST_LON, place.longitude.toFloat())
        }
    }

    fun setMetricUnits(context: Context, metric: Boolean) {
        prefs(context).edit { putBoolean(KEY_WEATHER_METRIC, metric) }
    }

    fun setAgendaDays(context: Context, days: Int) {
        prefs(context).edit { putInt(KEY_AGENDA_DAYS, days.coerceIn(1, 31)) }
    }

    fun setContactsLimit(context: Context, limit: Int) {
        prefs(context).edit { putInt(KEY_CONTACTS_LIMIT, limit.coerceIn(2, 24)) }
    }

    fun setClockShowDate(context: Context, show: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLOCK_SHOW_DATE, show) }
    }

    /** Null means every address; a set means only those. See [NavDestination.key]. */
    fun setNavigationSelection(context: Context, selection: Set<String>?) {
        prefs(context).edit {
            if (selection == null) remove(KEY_NAV_SELECTION) else putStringSet(KEY_NAV_SELECTION, selection)
        }
    }

    /** Accepts what people actually type: "example.com" becomes "https://example.com". */
    fun normalize(input: String): String {
        val url = input.trim()
        if (url.isEmpty()) return DEFAULT_URL
        return if (url.contains("://")) url else "https://$url"
    }

    /**
     * Watch for writes. The settings screen and the overlay share a process, so a change made in
     * one is visible to the other the moment it's written - this is what tells the overlay to go
     * and look, without waiting on a lifecycle callback the launcher may never send.
     *
     * The caller has to hold the returned listener: [SharedPreferences] keeps only a weak
     * reference to it.
     */
    fun observe(
        context: Context,
        onChange: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** A whole configuration, read once. */
data class HubSettings(
    val layout: List<HubModuleState>,
    val pageUrl: String,
    val metricUnits: Boolean,
    val agendaDays: Int,
    val contactsLimit: Int,
    val clockShowDate: Boolean,
    /** Null shows every address there is; a set narrows it to those keys. */
    val navigationSelection: Set<String>?,
) {
    val enabledModules: List<HubModule> get() = layout.filter { it.enabled }.map { it.module }
}
