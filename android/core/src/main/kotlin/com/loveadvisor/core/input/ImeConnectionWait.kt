package com.loveadvisor.core.input

enum class ImeConnectionStep { READY, FOCUS, WAIT, FAIL }
/** A visible IME may precede onStartInput. One focus attempt, then a bounded elapsed wait. */
class ImeConnectionWait {
    private var focused=false
    private var waitingSince:Long?=null
    private var focusResult:ComposerFocusResult?=null
    val failureMessage get()=focusResult?.failureMessage ?: "聊天输入框未就绪，请点输入框后重试"
    fun focusFinished(result:ComposerFocusResult,nowMillis:Long):ImeConnectionStep {
        focusResult=result
        waitingSince=nowMillis
        // A canceled gesture does not prove that onStartInput will never arrive.
        return if(result==ComposerFocusResult.CONTEXT_CHANGED)ImeConnectionStep.FAIL else ImeConnectionStep.WAIT
    }
    fun next(connected:Boolean,imeVisible:Boolean,nowMillis:Long):ImeConnectionStep {
        if(focusResult==ComposerFocusResult.CONTEXT_CHANGED)return ImeConnectionStep.FAIL
        if(connected)return ImeConnectionStep.READY
        if(!focused && !imeVisible) { focused=true;waitingSince=null;return ImeConnectionStep.FOCUS }
        val start=waitingSince ?: nowMillis.also { waitingSince=it }
        return if(nowMillis-start<4000)ImeConnectionStep.WAIT else ImeConnectionStep.FAIL
    }
}
object FillFeedback {
    fun failure(reason:String,copied:Boolean)= "填入失败：$reason"+if(copied)"；已复制，请手动粘贴" else ""
}
