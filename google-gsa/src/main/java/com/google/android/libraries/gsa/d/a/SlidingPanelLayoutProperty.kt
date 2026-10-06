package com.google.android.libraries.gsa.d.a

import android.util.Property

/**
 * A [Property] for animating a [SlidingPanelLayout]'s offset with the property animation system.
 */
internal class SlidingPanelLayoutProperty(type: Class<Int>, name: String) :
    Property<SlidingPanelLayout, Int>(type, name) {

    override fun get(panel: SlidingPanelLayout): Int = panel.panelOffsetPx

    override fun set(panel: SlidingPanelLayout, position: Int) {
        panel.updatePanelOffset(position)
    }
}
