package com.google.android.libraries.gsa.d.a

internal class TransparentOverlayController(
    private val overlayController: OverlayController,
) : PanelController {

    override fun onPanelDragged() {
        overlayController.setPanelBackgroundEnabled(false)
    }

    override fun startPanelDrag() {
        overlayController.setPanelBackgroundEnabled(false)
    }

    override fun setPanelEnabled(enabled: Boolean) = Unit

    override fun openPanel() {
        overlayController.setPanelBackgroundEnabled(false)
        overlayController.setFocusable(true)
        overlayController.setWindowAlpha(1.0f)
        updatePanelState(PanelState.OPEN_AS_LAYER)
    }

    override fun closePanel() {
        overlayController.isVisible = false
        updatePanelState(PanelState.CLOSED)
        overlayController.slidingPanelLayout.panelController = overlayController.panelController
    }

    override fun setPanelPosition(position: Float) = Unit

    override fun canInterceptTouchEvents(): Boolean = true

    private fun updatePanelState(newState: PanelState) {
        if (overlayController.panelState != newState) {
            overlayController.panelState = newState
            overlayController.setState(newState)
        }
    }
}
