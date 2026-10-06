package com.google.android.libraries.gsa.d.a

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt

@SuppressLint("ViewConstructor")
class OverlayControllerSlidingPanelLayout(
    private val overlayController: OverlayController,
) : SlidingPanelLayout(overlayController) {

    override fun determineScrollStart(ev: MotionEvent, scaleFactor: Float) {
        if (ev.findPointerIndex(activePointerId) == -1) return

        val deltaX = ev.x - downX
        val deltaY = ev.y - downY
        val absX = abs(deltaX)
        val absY = abs(deltaY)

        if (absX.compareTo(0f) == 0) return

        val angle = atan(absY / absX)

        val isDraggingInCorrectDirection = if (isRtl) deltaX < 0f else deltaX > 0f
        val canScroll = !isPanelOpen || isPageMoving

        if (!canScroll && isDraggingInCorrectDirection) return

        val allowIntercept = !canScroll && panelController?.canInterceptTouchEvents() == true

        // Angle threshold before a drag counts as a horizontal swipe
        if (allowIntercept || angle > Math.toRadians(60.0)) return

        if (angle > Math.toRadians(30.0)) {
            val normalizedAngle = (angle - Math.toRadians(30.0)) / Math.toRadians(30.0)
            val adjustedVelocity = sqrt(normalizedAngle).toFloat() * 4f + 1f
            super.determineScrollStart(ev, adjustedVelocity)
        } else {
            super.determineScrollStart(ev, scaleFactor)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun fitSystemWindows(insets: Rect): Boolean =
        !overlayController.isVisible || super.fitSystemWindows(insets)
}
