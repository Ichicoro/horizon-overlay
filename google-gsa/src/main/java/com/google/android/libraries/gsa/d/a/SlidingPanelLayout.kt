package com.google.android.libraries.gsa.d.a

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.Property
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

open class SlidingPanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context) {

    var downX: Float = 0f
    var downY: Float = 0f
    var lastMotionX: Float = 0f
    var totalMotionX: Float = 0f
    private var initialPanelOffset = 0f
    private var panelOffset = 0f
    var activePointerId: Int = -1
    private var velocityTracker: VelocityTracker? = null

    var isPanelOpen: Boolean = false
    var isPageMoving: Boolean = false
    var forceDrag: Boolean = false
    var settling: Boolean = false
    var panelPositionRatio: Float = 0f

    private val density: Float
    private val flingThresholdVelocity: Int
    private val minFlingVelocity: Int
    private val minSnapVelocity: Int
    val touchSlop: Int
    private val maxVelocity: Int
    val isRtl: Boolean

    /** The panel that slides in; added to this layout when set. */
    var foregroundPanel: View? = null
        set(value) {
            field = value
            value?.let { addView(it) }
        }

    /** Optional panel drawn behind [foregroundPanel]. */
    var backgroundPanel: View? = null
        set(value) {
            field?.let { removeView(it) }
            field = value
            value?.let { addView(it, 0) }
        }

    var panelOffsetPx: Int = 0

    private val panelInterpolator = SlidingPanelLayoutInterpolator(this)
    private val alphaInterpolator = DecelerateInterpolator(3.0f)

    var panelController: PanelController? = null

    var touchState: Int = 0

    init {
        val config = ViewConfiguration.get(context)
        touchSlop = config.scaledPagingTouchSlop
        maxVelocity = config.scaledMaximumFlingVelocity
        density = resources.displayMetrics.density
        flingThresholdVelocity = (300 * density).toInt()
        minFlingVelocity = (150 * density).toInt()
        minSnapVelocity = (1000 * density).toInt()
        isRtl = isRtl(resources)
    }

    fun updatePanelOffset(offsetPx: Int) {
        val offset = if (offsetPx <= 1) 0 else offsetPx
        val width = measuredWidth
        panelOffsetPx = offset.coerceIn(0, width)
        panelPositionRatio = panelOffsetPx.toFloat() / width

        foregroundPanel?.let { panel ->
            panel.translationX = (if (isRtl) -panelOffsetPx else panelOffsetPx).toFloat()
            if (ENABLE_ALPHA) {
                panel.alpha = maxOf(0.1f, alphaInterpolator.getInterpolation(panelPositionRatio))
            }
        }

        panelController?.setPanelPosition(panelPositionRatio)
    }

    fun startSettlingTo(target: Int, duration: Int) {
        startPanelDrag()
        settling = true
        panelInterpolator.animateTo(target, duration)
    }

    fun closePanel(duration: Int) {
        isPageMoving = true
        panelController?.setPanelEnabled(touchState == 1)
        settling = true
        panelInterpolator.animateTo(0, min(duration, 300))
    }

    private fun cnN() {
        touchState = 1
        isPageMoving = true
        settling = false
        panelInterpolator.cancelAnimation()
        if (USE_HARDWARE_LAYER) setLayerType(LAYER_TYPE_HARDWARE, null)
        panelController?.onPanelDragged()
    }

    private fun releaseTouch() {
        releaseVelocityTracker()
        forceDrag = false
        touchState = 0
        activePointerId = -1
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        acquireVelocityTrackerAndAddMovement(ev)

        if (childCount <= 0) {
            return super.onInterceptTouchEvent(ev)
        }
        val action = ev.action
        if (action == MotionEvent.ACTION_MOVE && touchState == 1) {
            return true
        }

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                initialPanelOffset = panelOffsetPx.toFloat()
                lastMotionX = downX
                totalMotionX = 0f
                activePointerId = ev.getPointerId(0)
                val distance = abs(panelInterpolator.finalX - panelOffsetPx)
                val settled = panelInterpolator.isFinished || distance < touchSlop / 3
                if (!settled || forceDrag) {
                    forceDrag = false
                    cnN()
                    panelOffset = downX
                }
            }

            MotionEvent.ACTION_MOVE -> if (activePointerId != -1) determineScrollStart(ev, 1.0f)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseTouch()

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
                releaseVelocityTracker()
            }
        }
        return touchState != 0
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (foregroundPanel == null) return super.onTouchEvent(ev)

        acquireVelocityTrackerAndAddMovement(ev)

        when (ev.actionMasked and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                initialPanelOffset = panelOffsetPx.toFloat()
                lastMotionX = downX
                totalMotionX = 0f
                activePointerId = ev.getPointerId(0)
                val settled = panelInterpolator.isFinished ||
                    abs(panelInterpolator.finalX - panelOffsetPx) < touchSlop / 3
                if (settled && !forceDrag) return true
                forceDrag = false
                cnN()
                panelOffset = downX
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchState == 1) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex == -1) return true
                    val x = ev.getX(pointerIndex)
                    totalMotionX += abs(x - lastMotionX)
                    val delta = x - panelOffset
                    lastMotionX = x
                    updatePanelOffset((initialPanelOffset + (if (isRtl) -delta else delta)).toInt())
                    return true
                }
                determineScrollStart(ev, 1.0f)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchState == 1) settleAfterFling()
                releaseTouch()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
                releaseVelocityTracker()
                return true
            }

            else -> return true
        }
    }

    private fun settleAfterFling() {
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000, maxVelocity.toFloat())
        var velocityX = tracker.getXVelocity(activePointerId).toInt()
        if (isRtl) velocityX = -velocityX

        val isFling = totalMotionX > 25 && abs(velocityX) > flingThresholdVelocity
        if (!isFling) {
            if (panelOffsetPx >= measuredWidth / 2) startSettlingTo(measuredWidth, 400) else closePanel(400)
            return
        }

        if (abs(velocityX) < minFlingVelocity) {
            if (velocityX >= 0) startSettlingTo(measuredWidth, 400) else closePanel(400)
            return
        }

        val travelled = (if (velocityX < 0) panelOffsetPx else measuredWidth - panelOffsetPx).toFloat()
        val projected = measuredWidth / 2f +
            sin(((min(1.0f, travelled / measuredWidth) - 0.5f) * 0.4712389).toDouble()).toFloat() *
            measuredWidth / 2f
        val duration = (abs(projected / maxOf(minSnapVelocity, abs(velocityX))) * 1000f).roundToInt() * 4
        if (velocityX > 0) startSettlingTo(measuredWidth, duration) else closePanel(duration)
    }

    open fun determineScrollStart(ev: MotionEvent, scaleFactor: Float) {
        val index = ev.findPointerIndex(activePointerId)
        if (index != -1) {
            val x = ev.getX(index)
            if (abs(x - downX) > Math.round(touchSlop * scaleFactor)) {
                totalMotionX += abs(lastMotionX - x)
                panelOffset = x
                lastMotionX = x
                cnN()
            }
        }
    }

    private fun acquireVelocityTrackerAndAddMovement(ev: MotionEvent) {
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(ev)
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.let {
            it.clear()
            it.recycle()
        }
        velocityTracker = null
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val index = (ev.action shr 8) and 0xFF
        if (ev.getPointerId(index) == activePointerId) {
            val newIndex = if (index == 0) 1 else 0
            val x = ev.getX(newIndex)
            panelOffset += x - lastMotionX
            downX = x
            lastMotionX = x
            activePointerId = ev.getPointerId(newIndex)
            velocityTracker?.clear()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val exactHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        backgroundPanel?.measure(exactWidth, exactHeight)
        foregroundPanel?.measure(exactWidth, exactHeight)
        setMeasuredDimension(width, height)
        updatePanelOffset((width * panelPositionRatio).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        backgroundPanel?.let { it.layout(0, 0, it.measuredWidth, it.measuredHeight) }
        foregroundPanel?.let {
            val width = it.measuredWidth
            val height = it.measuredHeight
            val left = if (isRtl) width else -width
            val right = if (isRtl) width * 2 else 0
            it.layout(left, 0, right, height)
        }
    }

    fun startPanelDrag() {
        isPageMoving = true
        panelController?.startPanelDrag()
    }

    fun onPanelFullyOpened() {
        updateLayer()
        isPanelOpen = true
        isPageMoving = false
        panelController?.openPanel()
    }

    internal fun updateLayer() {
        if (USE_HARDWARE_LAYER) {
            setLayerType(LAYER_TYPE_NONE, null)
        }
    }

    companion object {
        private const val ENABLE_ALPHA = false
        private const val USE_HARDWARE_LAYER = false

        val PANEL_X: Property<SlidingPanelLayout, Int> =
            SlidingPanelLayoutProperty(Int::class.javaObjectType, "panelX")

        fun isRtl(resources: Resources): Boolean =
            resources.configuration.layoutDirection == 1
    }
}
