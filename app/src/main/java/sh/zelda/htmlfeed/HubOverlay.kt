package sh.zelda.htmlfeed

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
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
import sh.zelda.htmlfeed.hub.HubActions
import sh.zelda.htmlfeed.hub.HubScreen
import sh.zelda.htmlfeed.hub.LocalHubActions

/**
 * The -1 screen: a stack of hub modules in a Compose host.
 *
 * The overlay window belongs to a Service, not an Activity, so it plays the part an Activity
 * normally would for [ComposeView]: it owns the lifecycle and the saved-state registry, and
 * feeds them the callbacks the launcher sends.
 */
class HubOverlay(context: Context) :
    OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** How far the panel has been pulled in, 0f..1f; drives the scrim and the content fade. */
    private var progress by mutableFloatStateOf(0f)
    private var settings by mutableStateOf(Settings.snapshot(context))

    /** Bumped on every resume, so the data-backed modules refetch when the panel reopens. */
    private var refreshKey by mutableIntStateOf(0)

    private var webView: WebView? = null

    /**
     * Opening an app from the panel.
     *
     * Order matters, and not for cosmetic reasons. The only background-activity-start exemption
     * this app qualifies for is "the calling uid has a visible non-app window" - the open panel
     * itself. Closing the panel first sets `isVisible = false`, which takes the window alpha to
     * zero and puts FLAG_NOT_TOUCHABLE back on, so the exemption is gone by the time the start
     * reaches the system and it's dropped with BAL_BLOCK: the panel shuts, nothing opens.
     *
     * So: start first, close second. The close is skipped if the start threw, which leaves the
     * panel up rather than dumping you on the home screen with nothing to show for the tap.
     */
    private val hubActions = HubActions { intent ->
        try {
            startActivity(intent)
            closePanelIfNeeded(1)
        } catch (e: Exception) {
            // Nothing on the device handles it. The panel stays open.
            Log.w(TAG, "Could not start $intent", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.snapshot(this)

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
    private fun OverlayContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = MAX_SCRIM_ALPHA * progress)
                )
        ) {
            CompositionLocalProvider(LocalHubActions provides hubActions) {
                HubScreen(
                    settings = settings,
                    refreshKey = refreshKey,
                    onWebView = { webView = it },
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep content clear of the status bar.
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .alpha(progress),
                )
            }
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
        // Pick up anything changed in settings while the overlay stayed alive.
        settings = Settings.snapshot(this)
        refreshKey++
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
        private const val TAG = "HubOverlay"

        /** Scrim opacity once the panel is fully open. */
        const val MAX_SCRIM_ALPHA = 1f
    }
}
