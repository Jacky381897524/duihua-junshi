package com.loveadvisor.core.overlay

import kotlin.math.roundToInt

/** Bounded native blur only; a zero radius means an opaque readable fallback. */
object FrostedPanelPolicy {
    fun radiusPx(api: Int, requested: Boolean, supported: Boolean, powerSave: Boolean, density: Float): Int {
        if (api < 31 || !requested || !supported || powerSave) return 0
        val safeDensity = density.takeIf { it.isFinite() && it > 0 } ?: 1f
        return (12f * safeDensity.coerceAtMost(4f)).roundToInt().coerceIn(1, 36)
    }
}
