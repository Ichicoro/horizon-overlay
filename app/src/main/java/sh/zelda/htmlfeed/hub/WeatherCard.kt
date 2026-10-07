package sh.zelda.htmlfeed.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.zelda.htmlfeed.WeatherPlace
import kotlin.math.roundToInt

/** Conditions now, the day's range, and the next few hours. */
@Composable
fun WeatherCard(
    place: WeatherPlace?,
    metricUnits: Boolean,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    val report by produceState<Result<WeatherReport>?>(null, place, metricUnits, refreshKey) {
        value = place?.let { WeatherRepository.load(it, metricUnits) }
    }

    HubCard(title = place?.name ?: "Weather", modifier = modifier) {
        val result = report
        when {
            place == null -> HubPlaceholder(
                message = "Pick a location in HTML Feed's settings to see the weather here.",
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            result == null -> HubPlaceholder(message = "Loading…")

            result.isFailure -> HubPlaceholder(
                message = "Couldn't reach the weather service.",
            )

            else -> WeatherBody(result.getOrThrow())
        }
    }
}

@Composable
private fun WeatherBody(report: WeatherReport) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
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
