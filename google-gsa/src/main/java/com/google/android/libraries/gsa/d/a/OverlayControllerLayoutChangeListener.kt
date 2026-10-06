package com.google.android.libraries.gsa.d.a

import android.view.View

internal class OverlayControllerLayoutChangeListener(
    private val overlayController: OverlayController,
) : View.OnLayoutChangeListener {

    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        overlayController.window.decorView.removeOnLayoutChangeListener(this)
        if (overlayController.panelState == PanelState.CLOSED) {
            val attributes = overlayController.window.attributes
            val previousAlpha = attributes.alpha
            attributes.alpha = 0.0f
            if (previousAlpha != attributes.alpha) {
                overlayController.window.attributes = attributes
            }
        }
    }
}
