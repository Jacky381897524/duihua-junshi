package com.loveadvisor.core.input

enum class ImeConnectionStep { READY, FOCUS, WAIT, FAIL }
/** A visible IME may precede onStartInput; never treat that transient state as failure. */
class ImeConnectionWait {
    private var focused=false
    private var polls=0
    fun next(connected:Boolean,imeVisible:Boolean):ImeConnectionStep {
        if(connected)return ImeConnectionStep.READY
        if(!focused && !imeVisible) { focused=true;return ImeConnectionStep.FOCUS }
        return if(polls++<8)ImeConnectionStep.WAIT else ImeConnectionStep.FAIL
    }
}
object FillFeedback {
    fun failure(reason:String,copied:Boolean)= "填入失败：$reason"+if(copied)"；已复制，请手动粘贴" else ""
}
