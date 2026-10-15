package com.loveadvisor.core.overlay

import kotlin.math.hypot

data class CapsuleDelta(val x:Float,val y:Float)
enum class CapsuleTouchAction { NONE, CLICK, DRAG_END }
class CapsuleDragGesture(private val holdMillis:Long,private val slop:Float) {
    private var active=false
    private var blocked=false
    private var armed=false
    private var locked=false
    private var startX=0f;private var startY=0f;private var downAt=0L
    fun setLocked(value:Boolean) { if(locked!=value) { cancel();locked=value } }
    fun down(x:Float,y:Float,time:Long) { active=true;blocked=false;armed=false;startX=x;startY=y;downAt=time }
    fun hold(now:Long):Boolean {
        if(!active || locked || blocked || armed || now-downAt<holdMillis) return false
        armed=true;return true
    }
    fun move(x:Float,y:Float,now:Long):CapsuleDelta? {
        if(!active || blocked) return null
        if(!armed) {
            if(now-downAt>=holdMillis) hold(now)
            else if(hypot(x-startX,y-startY)>slop) blocked=true
        }
        return if(armed && !blocked && !locked)CapsuleDelta(x-startX,y-startY) else null
    }
    fun finish(now:Long):CapsuleTouchAction {
        if(!active)return CapsuleTouchAction.NONE
        hold(now);active=false
        return when { blocked || locked && now-downAt>=holdMillis -> CapsuleTouchAction.NONE;armed -> CapsuleTouchAction.DRAG_END;else -> CapsuleTouchAction.CLICK }
    }
    fun cancel() { active=false;blocked=true;armed=false }
}
