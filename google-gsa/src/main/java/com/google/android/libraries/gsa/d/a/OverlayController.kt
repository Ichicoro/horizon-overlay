package com.google.android.libraries.gsa.d.a

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.android.libraries.launcherclient.LauncherOverlayCallback
import java.io.PrintWriter

open class OverlayController(
    context: Context,
    theme: Int,
    dialogTheme: Int,
) : DialogOverlayController(context, theme, dialogTheme) {

    var isRtl: Boolean = false
    var lastTouchTime: Long = 0
    var windowShift: Int = 0

    /** Package of the launcher this overlay is bound to. */
    var clientPackageName: String? = null

    lateinit var slidingPanelLayout: SlidingPanelLayout
    lateinit var container: FrameLayout

    val panelController: PanelController = OverlayControllerStateChanger(this)
    var touchStartX: Int = 0
    var acceptExternalMove: Boolean = false
    var overlayCallback: LauncherOverlayCallback? = null
    var panelState: PanelState = PanelState.CLOSED

    /** Whether the overlay window is drawn and touchable. */
    var isVisible: Boolean = true
        set(value) {
            field = value
            setFocusable(value)
            setWindowAlpha(if (value) 1.0f else 0.0f)
        }

    private var activityStateFlags = 0

    internal fun simulateMotionEvent(action: Int, position: Int, eventTime: Long) {
        val x = (if (isRtl) -position else position).toFloat()
        val event = MotionEvent.obtain(lastTouchTime, eventTime, action, x, 0.0f, 0)
        event.source = MotionEvent.ACTION_MOVE
        slidingPanelLayout.dispatchTouchEvent(event)
        event.recycle()
    }

    open fun onOptionsUpdated(options: Bundle) = Unit

    internal fun closeOverlay(): LauncherOverlayCallback? {
        updateActivityState(0)
        unregisterBackCallbackIfNeeded()
        try {
            windowManager.removeView(windowView)
        } catch (_: Throwable) {
        }
        windowView = null
        dismissAllDialogs()
        onDestroy()
        return overlayCallback
    }

    internal fun updateActivityState(newState: Int) {
        if (activityStateFlags != newState) {
            val wasStarted = (activityStateFlags and 1) != 0
            val wasResumed = (activityStateFlags and 2) != 0
            val shouldStart = (newState and 1) != 0
            val shouldResume = (newState and 2) != 0
            val shouldBeVisible = shouldStart || shouldResume

            activityStateFlags = (if (shouldBeVisible) 1 else 0) or (if (shouldResume) 2 else 0)

            if (!wasStarted && shouldBeVisible) onStart()
            if (!wasResumed && shouldResume) onResume()
            if (wasResumed && !shouldResume) onPause()
            if (wasStarted && !shouldBeVisible) onStop()
        }
    }

    override fun closePanelIfNeeded(flags: Int) {
        if (isOpen) {
            var shouldClose = (flags and 1) != 0
            if (panelState == PanelState.OPEN_AS_LAYER) {
                shouldClose = false
            }
            val duration = if (shouldClose) 750 else 0
            slidingPanelLayout.closePanel(duration)
            dismissAllDialogs()
        }
    }

    fun openPanelIfNeeded(flags: Int) {
        if (panelState == PanelState.CLOSED) {
            var asDrawer = (flags and 1) != 0
            val transparent = (flags and 2) != 0

            if (transparent) {
                slidingPanelLayout.panelController = TransparentOverlayController(this)
                asDrawer = false
            }

            val duration = if (asDrawer) 750 else 0
            slidingPanelLayout.startSettlingTo(duration, 0)
        }
    }

    override fun onBackPressed() {
        closePanelIfNeeded(1)
    }

    fun dump(out: PrintWriter, prefix: String) {
        out.println(prefix + "windowShift: " + windowShift)
        out.println(prefix + "acceptExternalMove: " + acceptExternalMove)
        out.println(prefix + "panelState: " + panelState)
        out.println(prefix + "activityStateFlags: " + activityStateFlags)
        out.println(prefix + "slidingPanelLayout: " + slidingPanelLayout)

        val subPrefix = "$prefix  "
        out.println(subPrefix + "panelPositionRatio: " + slidingPanelLayout.panelPositionRatio)
        out.println(subPrefix + "downX: " + slidingPanelLayout.downX)
        out.println(subPrefix + "downY: " + slidingPanelLayout.downY)
        out.println(subPrefix + "activePointerId: " + slidingPanelLayout.activePointerId)
        out.println(subPrefix + "touchState: " + slidingPanelLayout.touchState)
        out.println(subPrefix + "isPanelOpen: " + slidingPanelLayout.isPanelOpen)
        out.println(subPrefix + "isPageMoving: " + slidingPanelLayout.isPageMoving)
        out.println(subPrefix + "settling: " + slidingPanelLayout.settling)
        out.println(subPrefix + "forceDrag: " + slidingPanelLayout.forceDrag)
    }

    open fun Hn() = Unit

    open fun onCreate(savedInstanceState: Bundle?) = Unit

    open fun onPause() = Unit

    open fun onStop() = Unit

    open fun onStart() = Unit

    open fun onResume() = Unit

    open fun onDestroy() = Unit

    fun setTitle(title: CharSequence) {
        window.setTitle(title)
    }

    override fun getSystemService(name: String): Any? {
        if (Context.WINDOW_SERVICE == name) {
            return windowManager
        }
        return super.getSystemService(name)
    }

    val isOpen: Boolean
        get() = panelState == PanelState.OPEN_AS_DRAWER || panelState == PanelState.OPEN_AS_LAYER

    fun setFocusable(focusable: Boolean) {
        val attrs = window.attributes
        val flagsToChange = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        var changed = false

        if (focusable) {
            if ((attrs.flags and flagsToChange) != 0) {
                attrs.flags = attrs.flags and flagsToChange.inv()
                changed = true
            }
        } else {
            if ((attrs.flags and flagsToChange) != flagsToChange) {
                attrs.flags = attrs.flags or flagsToChange
                changed = true
            }
        }

        if (changed) {
            window.attributes = attrs
            if (focusable) windowView?.requestFocus()
        }
    }

    fun setWindowAlpha(alpha: Float) {
        val attrs = window.attributes
        val targetAlpha = alpha.coerceIn(0.0f, 1.0f)
        if (attrs.alpha != targetAlpha) {
            attrs.alpha = targetAlpha
            window.attributes = attrs
        }
    }

    open fun setState(newState: PanelState) {
        panelState = newState
    }

    open fun shouldHandleInput(): Boolean = false

    open fun onScroll(progress: Float) = Unit

    open fun setPanelBackgroundEnabled(enabled: Boolean) = Unit

    open fun applyByteBundle(holder: ByteBundleHolder) = Unit

    open fun configurePanel(enable: Boolean) = Unit
}
