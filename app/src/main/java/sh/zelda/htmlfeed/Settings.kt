package sh.zelda.htmlfeed

import android.content.Context
import androidx.core.content.edit

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
        HubModuleState(HubModule.WEB, enabled = false),
    )

    private const val PREFS = "htmlfeed"
    private const val KEY_LAYOUT = "module_layout"
    private const val KEY_URL = "page_url"
    private const val KEY_WEATHER_PLACE = "weather_place"
    private const val KEY_WEATHER_LAT = "weather_lat"
    private const val KEY_WEATHER_LON = "weather_lon"
    private const val KEY_WEATHER_METRIC = "weather_metric"
    private const val KEY_AGENDA_DAYS = "agenda_days"
    private const val KEY_CONTACTS_LIMIT = "contacts_limit"
    private const val KEY_CLOCK_SHOW_DATE = "clock_show_date"

    fun snapshot(context: Context): HubSettings {
        val prefs = prefs(context)
        val lat = prefs.getFloat(KEY_WEATHER_LAT, Float.NaN)
        val lon = prefs.getFloat(KEY_WEATHER_LON, Float.NaN)
        return HubSettings(
            layout = layout(context),
            pageUrl = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
            weather = WeatherPlace(
                name = prefs.getString(KEY_WEATHER_PLACE, null).orEmpty(),
                latitude = lat.toDouble(),
                longitude = lon.toDouble(),
            ).takeIf { !lat.isNaN() && !lon.isNaN() },
            metricUnits = prefs.getBoolean(KEY_WEATHER_METRIC, true),
            agendaDays = prefs.getInt(KEY_AGENDA_DAYS, 7),
            contactsLimit = prefs.getInt(KEY_CONTACTS_LIMIT, 8),
            clockShowDate = prefs.getBoolean(KEY_CLOCK_SHOW_DATE, true),
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

    fun setWeatherPlace(context: Context, place: WeatherPlace?) {
        prefs(context).edit {
            if (place == null) {
                remove(KEY_WEATHER_PLACE)
                remove(KEY_WEATHER_LAT)
                remove(KEY_WEATHER_LON)
            } else {
                putString(KEY_WEATHER_PLACE, place.name)
                putFloat(KEY_WEATHER_LAT, place.latitude.toFloat())
                putFloat(KEY_WEATHER_LON, place.longitude.toFloat())
            }
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

    /** Accepts what people actually type: "example.com" becomes "https://example.com". */
    fun normalize(input: String): String {
        val url = input.trim()
        if (url.isEmpty()) return DEFAULT_URL
        return if (url.contains("://")) url else "https://$url"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** A whole configuration, read once. */
data class HubSettings(
    val layout: List<HubModuleState>,
    val pageUrl: String,
    val weather: WeatherPlace?,
    val metricUnits: Boolean,
    val agendaDays: Int,
    val contactsLimit: Int,
    val clockShowDate: Boolean,
) {
    val enabledModules: List<HubModule> get() = layout.filter { it.enabled }.map { it.module }
}

data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
