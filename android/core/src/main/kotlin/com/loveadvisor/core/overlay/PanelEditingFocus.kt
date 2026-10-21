package com.loveadvisor.core.overlay

/** Reopening a retained page does not grant keyboard focus without a fresh field tap. */
class PanelEditingFocus {
    private var editable=false
    var requested=false;private set
    fun pageChanged(editable:Boolean) { this.editable=editable;release() }
    fun fieldTapped():Boolean { if(!editable)return false;requested=true;return true }
    fun release() { requested=false }
}
