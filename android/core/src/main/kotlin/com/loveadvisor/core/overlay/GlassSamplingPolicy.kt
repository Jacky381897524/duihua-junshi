package com.loveadvisor.core.overlay
object GlassSamplingPolicy {
    fun allowed(foreground:Boolean,panelVisible:Boolean,reading:Boolean)=foreground && panelVisible && !reading
}
