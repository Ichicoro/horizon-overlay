package com.google.android.libraries.gsa.d.a

import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.util.Pair
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import com.google.android.libraries.launcherclient.LauncherOverlayCallback
import java.io.PrintWriter
import kotlin.math.max

@Suppress("DEPRECATION")
internal abstract class OverlayControllerCallback(
    val binder: OverlayControllerBinder,
    private val overlayId: Int,
) : BaseCallback() {

    var overlayController: OverlayController? = null

    abstract fun createController(configuration: Configuration?): OverlayController

    override fun handleMessage(message: Message): Boolean {
        Log.d("OverlayControllerCallback", "handleMessage: " + message.what)
        return when (message.what) {
            0 -> handleInit(message)
            1 -> handleSetState(message)
            2 -> handleDestroy(message)
            6 -> handleCommand(message)
            7 -> handleVisibility(message)
            8 -> handleByteBundle(message)
            else -> false
        }
    }

    private fun handleInit(msg: Message): Boolean {
        if (msg.arg1 == 0) return true

        var stateBundle: Bundle? = null

        overlayController?.let {
            stateBundle = saveOverlayState(it)
            it.closeOverlay()
            overlayController = null
        }

        val pair = msg.obj as Pair<*, *>
        val args = pair.first as Bundle
        val callback = pair.second as LauncherOverlayCallback?

        val controller = createController(args.getParcelable("configuration"))
        overlayController = controller

        try {
            setupOverlayController(controller, args, args.getParcelable("layout_params")!!)
            restoreOverlayStateIfNeeded(stateBundle)

            controller.overlayCallback = callback
            controller.configurePanel(true)
            binder.a(callback, overlayId)
            controller.onOptionsUpdated(args)
        } catch (_: Throwable) {
            // fallback in case of error
            val fallback = Message.obtain()
            fallback.what = 2
            handleMessage(fallback)
            fallback.recycle()
        }

        return true
    }

    private fun handleSetState(msg: Message): Boolean {
        overlayController?.updateActivityState(msg.obj as Int)
        return true
    }

    private fun handleDestroy(msg: Message): Boolean {
        val controller = overlayController ?: return true
        val callback = controller.closeOverlay()
        overlayController = null
        if (msg.arg1 == 0) {
            binder.a(callback, 0)
        }
        return true
    }

    private fun handleCommand(msg: Message): Boolean {
        val controller = overlayController ?: return true
        val param = msg.arg2 and 1
        if (msg.arg1 == 1) {
            controller.openPanelIfNeeded(param)
        } else {
            controller.closePanelIfNeeded(param)
        }
        return true
    }

    private fun handleVisibility(msg: Message): Boolean {
        overlayController?.configurePanel(msg.arg1 == 1)
        return true
    }

    private fun handleByteBundle(msg: Message): Boolean {
        overlayController?.applyByteBundle(msg.obj as ByteBundleHolder)
        return true
    }

    private fun saveOverlayState(controller: OverlayController): Bundle = Bundle().apply {
        if (controller.panelState == PanelState.OPEN_AS_DRAWER) {
            putBoolean("open", true)
        }
        putParcelable("view_state", controller.window.saveHierarchyState())
    }

    private fun restoreOverlayStateIfNeeded(state: Bundle?) {
        if (state == null) return
        val controller = overlayController ?: return

        state.getBundle("view_state")?.let { controller.window.restoreHierarchyState(it) }

        if (state.getBoolean("open")) {
            val panel = controller.slidingPanelLayout
            panel.panelPositionRatio = 1.0f
            panel.panelOffsetPx = panel.measuredWidth
            panel.foregroundPanel?.translationX =
                (if (panel.isRtl) -panel.panelOffsetPx else panel.panelOffsetPx).toFloat()
            panel.startPanelDrag()
            panel.onPanelFullyOpened()
        }
    }

    private fun setupOverlayController(
        controller: OverlayController,
        args: Bundle,
        layoutParams: LayoutParams,
    ) {
        controller.isRtl = SlidingPanelLayout.isRtl(controller.resources)
        controller.clientPackageName = binder.packageName

        controller.window.setWindowManager(
            null,
            layoutParams.token,
            ComponentName(controller, controller.baseContext.javaClass).flattenToShortString(),
            true,
        )

        controller.windowManager = controller.window.windowManager

        val size = Point()
        controller.windowManager.defaultDisplay.getRealSize(size)
        controller.windowShift = -max(size.x, size.y)

        controller.slidingPanelLayout = OverlayControllerSlidingPanelLayout(controller)
        controller.container = FrameLayout(controller)
        controller.slidingPanelLayout.foregroundPanel = controller.container
        controller.slidingPanelLayout.panelController = controller.panelController

        layoutParams.width = LayoutParams.MATCH_PARENT
        layoutParams.height = LayoutParams.MATCH_PARENT
        layoutParams.flags = layoutParams.flags or 8650752 or
            LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_NOT_TOUCHABLE
        layoutParams.alpha = 0f
        layoutParams.dimAmount = 0f
        layoutParams.gravity = 3
        layoutParams.type = 4
        layoutParams.softInputMode = LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED

        controller.window.attributes = layoutParams
        controller.window.clearFlags(1048576)
        controller.onCreate(args)
        controller.window.setContentView(controller.slidingPanelLayout)

        controller.windowView = controller.window.decorView
        controller.windowManager.addView(controller.windowView, controller.window.attributes)
        controller.registerBackCallbackIfNeeded()

        controller.slidingPanelLayout.systemUiVisibility = 1792
        controller.isVisible = false
        controller.windowView?.addOnLayoutChangeListener(OverlayControllerLayoutChangeListener(controller))
    }

    override fun dump(printWriter: PrintWriter, str: String) {
        printWriter.println("$str mView: $overlayController")
        overlayController?.dump(printWriter, "$str  ")
    }
}
