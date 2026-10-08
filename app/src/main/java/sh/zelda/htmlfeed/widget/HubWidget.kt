package sh.zelda.htmlfeed.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import sh.zelda.htmlfeed.hub.hubSettingsIntent

/**
 * The chrome every hub widget shares, and the home-screen counterpart of `HubCard`: one tonal
 * surface, a header naming the module, then whatever the module has to show.
 *
 * The colours come from [GlanceTheme] with no scheme of its own, which is Material You - the
 * wallpaper's palette on Android 12 and up, and Glance's own baseline M3 palette below it, where
 * there are no system colours to read.
 */
@Composable
fun HubWidgetSurface(
    title: String,
    actionLabel: String? = null,
    action: Action? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            // Tells the launcher which view is the widget's background, so Android 12's own
            // corner rounding and the "hide widget background" setting both land on it.
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            // Matches the panel's 28dp cards as closely as a widget can. Outline clipping is an
            // API 31 feature, so below that this is ignored and the surface stays square - the
            // same releases that have no dynamic colour to offer either.
            .cornerRadius(24.dp)
            .padding(16.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = WidgetText.title,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (actionLabel != null && action != null) {
                Text(
                    text = actionLabel,
                    style = WidgetText.action,
                    maxLines = 1,
                    modifier = GlanceModifier
                        .wrapContentWidth()
                        .clickable(action)
                        .padding(start = 8.dp),
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        content()
    }
}

/**
 * What a module shows when it has nothing, or hasn't been given the permission it needs.
 *
 * Tapping it opens settings, which is where both permissions are asked for and where every
 * module's options live - the same deal the cards in the panel offer.
 */
@Composable
fun WidgetPlaceholder(message: String) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(hubSettingsIntent(context))),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = message, style = WidgetText.body, maxLines = 4)
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(text = "Open settings", style = WidgetText.action, maxLines = 1)
    }
}

/**
 * The panel's type scale, restated in the handful of styles a widget needs.
 *
 * Not shared with `HubTypography`: that one is Compose's [androidx.compose.ui.text.TextStyle] and
 * a widget's is Glance's, and there's no conversion between them.
 */
object WidgetText {
    val title: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

    val action: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

    /** A row's first line: the event, the person, the place. */
    val primary: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 14.sp,
        )

    /** The line under it - a time, an address. */
    val secondary: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp,
        )

    val body: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 13.sp,
        )
}

/**
 * The container role a name always gets, picked the same way `avatarColors` picks it in the
 * panel, so the same person is the same colour in both places.
 */
@Composable
fun widgetAvatarColors(name: String): Pair<ColorProvider, ColorProvider> {
    val colors = GlanceTheme.colors
    val palette = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
    )
    val index = (name.hashCode().toLong() and 0xFFFFFFFFL) % palette.size
    return palette[index.toInt()]
}
