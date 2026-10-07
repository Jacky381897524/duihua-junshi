package com.loveadvisor.core.input
import com.loveadvisor.core.capture.WindowSnapshot
import com.loveadvisor.core.model.*
data class FillTarget(val snapshot:ChatSnapshot,val window:WindowSnapshot,val editable:NodeRecord,val handle:Any?=null)
interface FillPlatform {
    fun current():FillTarget?
    fun focus(target:FillTarget):Boolean
    fun setText(target:FillTarget,text:String):Boolean
    fun paste(target:FillTarget):Boolean
    fun copy(text:String)
}
enum class FillStatus { FILLED,COPIED_ONLY,STALE }
data class FillOutcome(val status:FillStatus,val message:String)
class FillTransaction(private val guard:FillGuard,private val platform:FillPlatform) {
    fun fill(token:SessionToken,text:String):FillOutcome {
        fun stale()=FillOutcome(FillStatus.STALE,"会话已变化，请重新读取")
        fun copy(reason:String):FillOutcome { platform.copy(text); return FillOutcome(FillStatus.COPIED_ONLY,reason) }
        fun fresh():FillTarget?=platform.current()?.takeIf { guard.verify(token,it.snapshot,it.window,it.editable).allowed }
        if(!guard.isCurrent(token)) return stale()
        val first=fresh() ?: return if(guard.isCurrent(token)) copy("无法确认底部输入框，已复制") else stale()
        try {
            if(!platform.focus(first)) return copy("无法聚焦输入框，已复制")
            val second=fresh() ?: return if(guard.isCurrent(token)) copy("无法确认聚焦后的输入框，已复制") else stale()
            if(!platform.setText(second,text)) {
                if(fresh()==null) return if(guard.isCurrent(token)) copy("无法确认输入框，已复制") else stale()
                platform.copy(text)
                val pasteTarget=fresh() ?: return if(guard.isCurrent(token)) copy("无法确认粘贴目标，请手动粘贴") else stale()
                platform.paste(pasteTarget)
            }
            val observed=fresh()
            if(!guard.isCurrent(token)) return stale()
            return if(observed?.editable?.text==text) FillOutcome(FillStatus.FILLED,"已填入草稿，请核对后手动发送") else copy("无法确认写入结果，已复制，请核对草稿")
        } catch(_:Exception) { return if(guard.isCurrent(token)) copy("输入操作失败，已复制，请核对草稿") else stale() }
    }
}
