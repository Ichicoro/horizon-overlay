package com.google.android.libraries.gsa.d.a

import android.util.Log

internal class OverlayControllerStateChanger(
    private val overlayController: OverlayController,
) : PanelController {

    override fun onPanelDragged() {
        updatePanelState(PanelState.DRAGGING)
        overlayController.setPanelBackgroundEnabled(true)
        overlayController.isVisible = true
    }

    override fun startPanelDrag() {
        updatePanelState(PanelState.DRAGGING)
        overlayController.setPanelBackgroundEnabled(true)
        overlayController.isVisible = true
    }

    override fun setPanelEnabled(enabled: Boolean) {
        if (enabled) {
            overlayController.Hn()
        } else {
            overlayController.isVisible = false
        }
        updatePanelState(PanelState.DRAGGING)
    }

    override fun openPanel() {
        Log.d("OverlayController", "Opening panel in drawer mode")
        updatePanelState(PanelState.OPEN_AS_DRAWER)
    }

    override fun setPanelPosition(position: Float) {
        val callback = overlayController.overlayCallback
        if (callback != null && !position.isNaN()) {
            try {
                callback.overlayScrollChanged(position)
                overlayController.onScroll(position)
            } catch (_: Throwable) {
                // Optionally log the exception if needed
            }
        }
    }

    override fun closePanel() {
        overlayController.isVisible = false
        updatePanelState(PanelState.CLOSED)
    }

    override fun canInterceptTouchEvents(): Boolean = overlayController.shouldHandleInput()

    private fun updatePanelState(newState: PanelState) {
        if (overlayController.panelState != newState) {
            overlayController.panelState = newState
            overlayController.setState(newState)
        }
    }
}
