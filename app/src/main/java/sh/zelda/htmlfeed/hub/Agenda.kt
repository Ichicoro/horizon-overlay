package sh.zelda.htmlfeed.hub

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** One upcoming event. Times are epoch millis, UTC for all-day events. */
data class AgendaEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int?,
) {
    val viewIntent: Intent
        get() = Intent(
            Intent.ACTION_VIEW,
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
}

object CalendarRepository {
    const val PERMISSION = Manifest.permission.READ_CALENDAR

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Events that start between now and [days] days out, soonest first.
     *
     * Queries Instances rather than Events so a repeating event is already expanded into the
     * individual occurrences, which is what "what's next" actually means.
     */
    suspend fun upcoming(context: Context, days: Int, limit: Int = 12): List<AgendaEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()
            // Start at midnight so an all-day event happening today isn't already behind us:
            // all-day instances are stamped at UTC midnight, which can sit before "now".
            val from = startOfToday()
            val to = from + TimeUnit.DAYS.toMillis(days.toLong())
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.STATUS,
            )
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(from.toString())
                .appendPath(to.toString())
                .build()
            val now = System.currentTimeMillis()
            val events = mutableListOf<AgendaEvent>()
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < limit) {
                    val allDay = cursor.getInt(4) != 0
                    val end = cursor.getLong(3)
                    // Drop what's already over, but keep something in progress.
                    if (!allDay && end < now) continue
                    events += AgendaEvent(
                        eventId = cursor.getLong(0),
                        title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = cursor.getLong(2),
                        end = end,
                        allDay = allDay,
                        location = cursor.getString(5)?.takeIf { it.isNotBlank() },
                        color = cursor.getInt(6).takeIf { it != 0 },
                    )
                }
            }
            events
        }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Opens the calendar app on today. */
    fun openCalendarIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build(),
    )
}

/**
 * The line under an event's title: when it is, and where, as much of it as there is.
 *
 * Lives here rather than in the card because the home-screen widget shows the same line, and an
 * event that reads "Tomorrow · 09:30" in the panel has to read that on the widget too.
 */
fun AgendaEvent.detailLine(context: Context, locale: Locale): String =
    listOfNotNull(dayLabel(locale), timeLabel(context, locale), location).joinToString(" · ")

/** "Today", "Tomorrow", or a weekday - whichever is shortest to read. */
fun AgendaEvent.dayLabel(locale: Locale): String {
    // All-day instances are stamped at UTC midnight, so they have to be read in UTC to land on
    // the calendar day the user actually sees.
    val zone = timeZone
    val daysApart = epochDay(begin, zone) - epochDay(System.currentTimeMillis(), zone)
    fun format(pattern: String) = SimpleDateFormat(pattern, locale)
        .apply { timeZone = zone }
        .format(Date(begin))

    return when (daysApart) {
        0L -> "Today"
        1L -> "Tomorrow"
        in 2L..6L -> format("EEEE")
        else -> format("EEE d MMM")
    }
}

/** The clock time, in whichever of the two formats the phone is set to - or "all day". */
fun AgendaEvent.timeLabel(context: Context, locale: Locale): String {
    if (allDay) return "all day"
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return SimpleDateFormat(pattern, locale).format(Date(begin))
}

private val AgendaEvent.timeZone: TimeZone
    get() = if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()

/** Days since the epoch in [zone]; floor division keeps it right for dates before 1970. */
private fun epochDay(millis: Long, zone: TimeZone): Long =
    Math.floorDiv(millis + zone.getOffset(millis), TimeUnit.DAYS.toMillis(1))
