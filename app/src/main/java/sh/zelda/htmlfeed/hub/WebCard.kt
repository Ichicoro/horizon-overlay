package sh.zelda.htmlfeed.hub

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The original -1 screen, now one module among the rest: any URL in a WebView.
 *
 * [onWebView] hands the view up to the overlay, which needs it for back navigation and for
 * pausing timers when the panel closes.
 */
@Composable
@SuppressLint("SetJavaScriptEnabled")
fun WebModule(
    url: String,
    onWebView: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) { onDispose { onWebView(null) } }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient() // keep navigation inside the overlay
                onWebView(this)
            }
        },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        },
        modifier = modifier,
    )
}

/** The card form, for when the page shares the panel with other modules. */
@Composable
fun WebCard(
    url: String,
    onWebView: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentShapes.single,
    height: Dp = 320.dp,
) {
    HubCard(title = "Page", modifier = modifier, shape = shape) {
        WebModule(
            url = url,
            onWebView = onWebView,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                // Match the card's own corner rather than cutting a square hole in it.
                .clip(MaterialTheme.shapes.large),
        )
    }
}
