package sh.zelda.htmlfeed

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.libraries.gsa.d.a.OverlayController
import com.google.android.libraries.gsa.d.a.PanelState

/**
 * The -1 screen: a WebView in a Compose host.
 *
 * The overlay window belongs to a Service, not an Activity, so it plays the part an Activity
 * normally would for [ComposeView]: it owns the lifecycle and the saved-state registry, and
 * feeds them the callbacks the launcher sends.
 */
class HtmlOverlay(context: Context) :
    OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** How far the panel has been pulled in, 0f..1f; drives the scrim and the page fade. */
    private var progress by mutableFloatStateOf(0f)
    private var url by mutableStateOf("")

    private var webView: WebView? = null
    private var loadedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = Settings.pageUrl(this)

        savedStateRegistryController.performRestore(null) // must precede ON_CREATE
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // Start here rather than waiting for onStart: the launcher may never send an
        // activity state, and recomposition is paused below STARTED.
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // Compose resolves the window recomposer from the window's content view (the panel
        // layout the library installs), not from the ComposeView's own parent, so the owner
        // has to sit there. It exists by now: the library builds it before calling onCreate.
        slidingPanelLayout.setViewTreeLifecycleOwner(this)
        slidingPanelLayout.setViewTreeSavedStateRegistryOwner(this)

        // The panel swallows insets whenever the overlay is hidden
        // (OverlayControllerSlidingPanelLayout.fitSystemWindows returns true then), and the
        // window's first dispatch lands while it is still hidden, so everything below it would
        // only ever see zeros. A listener takes the place of that path and passes them down.
        ViewCompat.setOnApplyWindowInsetsListener(slidingPanelLayout) { _, insets -> insets }

        val composeView = ComposeView(this).apply {
            setContent { HtmlFeedTheme { OverlayContent() } }
        }
        container.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    @Composable
    @SuppressLint("SetJavaScriptEnabled")
    private fun OverlayContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = MAX_SCRIM_ALPHA * progress)
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient() // keep navigation inside the overlay
                        webView = this
                    }
                },
                update = { view ->
                    if (url != loadedUrl) {
                        loadedUrl = url
                        view.loadUrl(url)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    // Keep content clear of the status bar.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .alpha(progress),
            )
        }
    }

    /** Called for every frame of the swipe (and of the settle animation), 0f..1f. */
    override fun onScroll(progress: Float) {
        super.onScroll(progress)
        this.progress = progress.coerceIn(0f, 1f)
    }

    override fun setState(newState: PanelState) {
        super.setState(newState)
        // Snap for the paths that never scroll: opened programmatically, or restored open.
        when (newState) {
            PanelState.OPEN_AS_DRAWER, PanelState.OPEN_AS_LAYER -> progress = 1f
            PanelState.CLOSED -> progress = 0f
            else -> Unit
        }
    }

    override fun onBackPressed() {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        webView?.onResume()
        // Pick up a new page chosen in settings while the overlay stayed alive.
        url = Settings.pageUrl(this)
    }

    override fun onPause() {
        webView?.onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private companion object {
        /** Scrim opacity once the panel is fully open. */
//        const val MAX_SCRIM_ALPHA = 0.78f
        const val MAX_SCRIM_ALPHA = 1
    }
}
