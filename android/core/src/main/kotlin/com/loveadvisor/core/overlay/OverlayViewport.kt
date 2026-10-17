package com.loveadvisor.core.overlay

import com.loveadvisor.core.model.RectI

object OverlayViewport {
    /** Bars and cutouts overlap; use their maximum, not their sum. Coordinates remain global. */
    fun available(screen: RectI, bars: InsetsI, cutout: InsetsI = InsetsI(), imeTop: Int? = null, density: Float = 1f): RectI {
        val left = (screen.left + maxOf(0, bars.left, cutout.left)).coerceAtMost(screen.right - 1)
        val top = (screen.top + maxOf(0, bars.top, cutout.top)).coerceAtMost(screen.bottom - 1)
        val right = (screen.right - maxOf(0, bars.right, cutout.right)).coerceAtLeast(left + 1)
        val barBottom = screen.bottom - maxOf(0, bars.bottom, cutout.bottom)
        val d = density.takeIf { it.isFinite() && it > 0 } ?: 1f
        val keyboardBottom = imeTop?.takeIf { it > screen.top && it <= screen.bottom }
            ?.let { OverlayGeometry.composerSafeBottom(it, d) } ?: screen.bottom
        return RectI(left, top, right, minOf(barBottom, keyboardBottom).coerceAtLeast(top + 1))
    }
}
