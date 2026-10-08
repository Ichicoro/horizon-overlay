package sh.zelda.htmlfeed.hub

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class HourlyTemperature(
    val hourOfDay: Int,
    val temperature: Double,
    val code: Int,
)

data class WeatherReport(
    val placeName: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val code: Int,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val unitSuffix: String,
    val hourly: List<HourlyTemperature>,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val condition: String get() = WeatherCodes.describe(code)
    val glyph: String get() = WeatherCodes.glyph(code, isDay)
}

/**
 * Open-Meteo: free, no key, no account, no attribution requirement for non-commercial use.
 *
 * Results are cached in memory for [CACHE_TTL_MS] so swiping the panel open repeatedly doesn't
 * turn into a request each time.
 */
object WeatherRepository {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)

    private var cacheKey: String? = null
    private var cached: WeatherReport? = null

    /** Drops the cache so the next [load] really goes to the network. */
    fun invalidate() {
        cacheKey = null
        cached = null
    }

    /**
     * Locates the phone, then forecasts for wherever that is. [LocationUnavailable] is the
     * failure the card tells apart, since it's the one the user can do something about.
     */
    suspend fun loadCurrent(context: Context, metric: Boolean): Result<WeatherReport> {
        val place = LocationRepository.current(context)
            ?: return Result.failure(LocationUnavailable())
        return load(place, metric)
    }

    suspend fun load(place: WeatherPlace, metric: Boolean): Result<WeatherReport> {
        val key = cacheKey(place, metric)
        cached?.let { hit ->
            if (cacheKey == key && System.currentTimeMillis() - hit.fetchedAt < CACHE_TTL_MS) {
                return Result.success(hit)
            }
        }
        return onIo { fetchForecast(place, metric) }.onSuccess {
            cacheKey = key
            cached = it
        }
    }

    /**
     * Coordinates rounded to about a kilometre. A fix drifts by a few metres between reads, and
     * on the raw numbers that would miss the cache every time the panel is opened.
     */
    private fun cacheKey(place: WeatherPlace, metric: Boolean): String =
        "%.2f,%.2f,%s".format(place.latitude, place.longitude, metric)

    private fun fetchForecast(place: WeatherPlace, metric: Boolean): WeatherReport {
        val url = FORECAST.toUri().buildUpon()
            .appendQueryParameter("latitude", place.latitude.toString())
            .appendQueryParameter("longitude", place.longitude.toString())
            .appendQueryParameter("current", "temperature_2m,apparent_temperature,weather_code,is_day")
            .appendQueryParameter("hourly", "temperature_2m,weather_code")
            .appendQueryParameter("daily", "temperature_2m_max,temperature_2m_min")
            .appendQueryParameter("forecast_days", "2")
            .appendQueryParameter("timezone", "auto")
            .appendQueryParameter("temperature_unit", if (metric) "celsius" else "fahrenheit")
            .build()
            .toString()
        return parseForecast(JSONObject(getString(url)), place, metric)
    }

    /** The fetch is blocking HTTP, and it's started from a composition. */
    private suspend fun <T> onIo(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching(block) }

    private fun parseForecast(
        json: JSONObject,
        place: WeatherPlace,
        metric: Boolean,
    ): WeatherReport {
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.optJSONObject("hourly")
        val currentTime = current.getString("time") // local, "2026-10-07T14:00"
        return WeatherReport(
            placeName = place.name,
            temperature = current.getDouble("temperature_2m"),
            apparentTemperature = current.optDouble(
                "apparent_temperature",
                current.getDouble("temperature_2m"),
            ),
            code = current.optInt("weather_code", 0),
            isDay = current.optInt("is_day", 1) == 1,
            high = daily.getJSONArray("temperature_2m_max").getDouble(0),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0),
            unitSuffix = if (metric) "°C" else "°F",
            hourly = parseHourly(hourly, currentTime),
        )
    }

    /**
     * The next few hours. The hourly series starts at midnight local, so the current hour's
     * timestamp is what locates "now" in it — no timezone math on our side.
     */
    private fun parseHourly(hourly: JSONObject?, currentTime: String): List<HourlyTemperature> {
        hourly ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code")
        val currentHour = currentTime.substringBefore(':')
        var start = (0 until times.length()).firstOrNull {
            times.getString(it).startsWith(currentHour)
        } ?: return emptyList()
        start += 1 // "now" already has its own big number above the row
        return (start until minOf(start + 6, times.length(), temps.length())).map { i ->
            HourlyTemperature(
                hourOfDay = times.getString(i).substringAfter('T').substringBefore(':').toInt(),
                temperature = temps.getDouble(i),
                code = codes?.optInt(i, 0) ?: 0,
            )
        }
    }

    private fun getString(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode} from $url")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** No permission, no provider, or no fix in time - told apart from a failed forecast fetch. */
class LocationUnavailable : Exception("No location fix")

/** Pixel's own weather app, which is where a tap on the card goes. */
private const val PIXEL_WEATHER_PACKAGE = "com.google.android.apps.weather"

/**
 * Opens Pixel Weather, falling back to the Google app's weather card when it isn't installed -
 * on a non-Pixel there's no system weather app to hand off to.
 */
fun weatherAppIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(PIXEL_WEATHER_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=weather".toUri())

/** WMO weather interpretation codes, as Open-Meteo reports them. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Rain showers"
        82 -> "Violent showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "—"
    }

    fun glyph(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "☀️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "🌨️"
        in 80..82 -> "🌧️"
        85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }
}
