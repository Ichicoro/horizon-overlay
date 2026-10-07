package sh.zelda.htmlfeed.hub

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * The top of the panel: time, date, and a greeting.
 *
 * Deliberately not a [HubCard] — it reads as a header, and a box around the time looks like a
 * widget rather than the top of a page.
 */
@Composable
fun ClockCard(showDate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick on the minute rather than every second: nothing here shows seconds, and the panel is
    // usually on screen for a few seconds at a time.
    LaunchedEffect(Unit) {
        while (true) {
            val millis = System.currentTimeMillis()
            now = millis
            delay(60_000 - millis % 60_000)
        }
    }

    val timeFormat = remember(locale, DateFormat.is24HourFormat(context)) {
        SimpleDateFormat(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
            locale,
        )
    }
    val dateFormat = remember(locale) { SimpleDateFormat("EEEE, d MMMM", locale) }
    val date = Date(now)

    Column(modifier = modifier) {
        Text(
            text = greeting(now),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = timeFormat.format(date),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showDate) {
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun greeting(now: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..4 -> "Still up"
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
