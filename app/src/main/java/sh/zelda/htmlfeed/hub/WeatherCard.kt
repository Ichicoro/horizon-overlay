package sh.zelda.htmlfeed.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Conditions now, the day's range, and the next few hours, for wherever the phone is.
 *
 * The fetch itself lives in [HubScreen]: it's the one module that waits on the network, so it's
 * what the pull-to-refresh indicator times itself against. [report] is null while in flight.
 */
@Composable
fun WeatherCard(
    report: Result<WeatherReport>?,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentShapes.single,
) {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    val granted = LocationRepository.hasPermission(context)

    // The title is the located place, so it only has a name once the forecast is in.
    HubCard(
        title = report?.getOrNull()?.placeName ?: "Weather",
        modifier = modifier,
        shape = shape,
    ) {
        val result = report
        when {
            !granted -> HubPlaceholder(
                message = "Allow location access in HTML Feed's settings to see the weather " +
                    "where you are.",
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            result == null -> HubPlaceholder(message = "Loading…")

            result.exceptionOrNull() is LocationUnavailable -> HubPlaceholder(
                message = "Couldn't work out where you are. Check that location is turned on.",
            )

            result.isFailure -> HubPlaceholder(
                message = "Couldn't reach the weather service.",
            )

            else -> WeatherBody(
                report = result.getOrThrow(),
                onClick = { actions.launch(weatherAppIntent(context)) },
            )
        }
    }
}

@Composable
private fun WeatherBody(report: WeatherReport, onClick: () -> Unit) {
    // The whole body is the target rather than the glyph alone: there's nothing else in here to
    // tap, so anywhere in the card should get you to the forecast.
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = report.glyph,
                style = MaterialTheme.typography.displaySmall,
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = "${report.temperature.roundToInt()}${report.unitSuffix}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${report.condition} · feels like " +
                        "${report.apparentTemperature.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "H ${report.high.roundToInt()}° · L ${report.low.roundToInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (report.hourly.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                report.hourly.forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%02d".format(hour.hourOfDay),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = WeatherCodes.glyph(hour.code, hour.hourOfDay in 7..19),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        Text(
                            text = "${hour.temperature.roundToInt()}°",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
