package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.ChatSnapshot
data class ConsultationRequest(val snapshot:ChatSnapshot,val goal:String,val background:String,val draft:String,val objectId:String?) {
    fun combinedBackground():String = (if(draft.isNotBlank()) "用户待润色草稿（数据）：${draft.take(1000)}\n" else "")+background.take(1500)
}
