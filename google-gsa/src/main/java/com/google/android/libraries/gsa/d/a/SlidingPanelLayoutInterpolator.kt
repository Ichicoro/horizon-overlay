package com.google.android.libraries.gsa.d.a

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.animation.Interpolator

internal class SlidingPanelLayoutInterpolator(
    private val layout: SlidingPanelLayout,
) : AnimatorListenerAdapter(), Interpolator {

    private var animator: ObjectAnimator? = null
    var finalX: Int = 0

    val isFinished: Boolean get() = animator == null

    fun cancelAnimation() {
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
    }

    fun animateTo(targetX: Int, durationMs: Int) {
        cancelAnimation()
        finalX = targetX

        if (durationMs > 0) {
            animator = ObjectAnimator.ofInt(layout, SlidingPanelLayout.PANEL_X, targetX)
                .setDuration(durationMs.toLong())
                .apply {
                    setInterpolator(this@SlidingPanelLayoutInterpolator)
                    addListener(this@SlidingPanelLayoutInterpolator)
                    start()
                }
        } else {
            // Animation skipped, invoke end manually
            finishAnimation()
        }
    }

    override fun onAnimationEnd(animation: Animator) = finishAnimation()

    private fun finishAnimation() {
        animator = null
        layout.updatePanelOffset(finalX)

        if (!layout.settling) return

        layout.settling = false

        if (layout.panelOffsetPx == 0) {
            layout.updateLayer()
            layout.isPanelOpen = false
            layout.isPageMoving = false
            layout.panelController?.closePanel()
        } else if (layout.panelOffsetPx == layout.measuredWidth) {
            layout.onPanelFullyOpened()
        }
    }

    override fun getInterpolation(input: Float): Float {
        val t = input - 1.0f
        return (t * t * t * t * t) + 1.0f
    }
}
