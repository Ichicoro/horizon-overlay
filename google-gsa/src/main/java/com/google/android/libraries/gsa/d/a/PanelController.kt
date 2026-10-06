package com.google.android.libraries.gsa.d.a

interface PanelController {
    fun setPanelPosition(position: Float)

    fun onPanelDragged()

    fun startPanelDrag()

    fun openPanel()

    fun closePanel()

    fun canInterceptTouchEvents(): Boolean

    fun setPanelEnabled(enabled: Boolean)
}
