package com.google.android.libraries.gsa.d.a

import android.content.res.Configuration
import android.os.Message
import java.io.PrintWriter

internal class MinusOneOverlayCallback(
    private val overlaysController: OverlaysController,
    overlayControllerBinder: OverlayControllerBinder,
) : OverlayControllerCallback(overlayControllerBinder, 3) {

    override fun createController(configuration: Configuration?): OverlayController =
        overlaysController.createController(
            configuration,
            binder.serverVersion,
            binder.clientVersion,
        )

    override fun dump(printWriter: PrintWriter, str: String) {
        printWriter.println(str + "MinusOneOverlayCallback")
        super.dump(printWriter, str)
    }

    override fun handleMessage(message: Message): Boolean {
        if (super.handleMessage(message)) {
            return true
        }

        val controller = overlayController ?: return false
        val timestamp = message.`when`

        return when (message.what) {
            3 -> {
                if (!controller.isOpen) {
                    val panel = controller.slidingPanelLayout
                    if (panel.panelOffsetPx < panel.touchSlop) {
                        panel.updatePanelOffset(0)
                        controller.acceptExternalMove = true
                        controller.touchStartX = 0
                        panel.forceDrag = true
                        controller.lastTouchTime = timestamp - 30
                        controller.simulateMotionEvent(0, controller.touchStartX, controller.lastTouchTime)
                        controller.simulateMotionEvent(2, controller.touchStartX, timestamp)
                    }
                }
                true
            }

            4 -> {
                val progress = message.obj
                if (controller.acceptExternalMove && progress is Float) {
                    controller.touchStartX =
                        (progress * controller.slidingPanelLayout.measuredWidth).toInt()
                    controller.simulateMotionEvent(2, controller.touchStartX, timestamp)
                }
                true
            }

            5 -> {
                if (controller.acceptExternalMove) {
                    controller.simulateMotionEvent(1, controller.touchStartX, timestamp)
                }
                controller.acceptExternalMove = false
                true
            }

            else -> false
        }
    }
}
