package com.loveadvisor.core.advisor
import java.text.Normalizer
import java.util.Locale
class AdvisorValidator {
    fun validate(batch:AdvisorBatch):ValidationResult {
        val errors=mutableListOf<String>()
        if(batch.replies.size!=6) errors.add("必须恰好六条候选回复")
        if(batch.compact) {
            if(batch.replies.none { it.strategy in setOf(Strategy.APPRECIATION,Strategy.TEASING,Strategy.INVITATION) }) errors.add("至少一条恋爱大师的欣赏、调侃或邀约策略")
            if(batch.replies.map { it.strategy }.distinct().size<6) errors.add("六条需使用不同策略")
        } else if(batch.replies.count { it.strategy.romance }<2) errors.add("至少两条明确使用恋爱大师表达、节奏、修复或关系推进策略")
        if(batch.replies.map { normalize(it.text) }.distinct().size!=batch.replies.size) errors.add("候选正文重复，需改为不同表达或行动")
        if(batch.replies.map { it.id }.distinct().size!=batch.replies.size) errors.add("候选 id 重复")
        if(batch.analysis.risk!=null && batch.analysis.risk !in 1..9) errors.add("风险等级须为 1–9 或 null")
        if(batch.analysis.intent.isBlank() || batch.analysis.action.isBlank()) errors.add("缺少意图推测或具体行动")
        batch.replies.forEach { reply ->
            if(reply.id.isBlank() || reply.text.isBlank() || length(reply.text)>300) errors.add("回复正文须为 1–300 字")
            if(!batch.compact) {
                if(length(reply.reason)<6 || length(reply.reason)>200 || reply.reason.trim() in listOf("恋爱大师",reply.strategy.label)) errors.add("每条须有具体选择理由，不能只有策略标签")
                if(reply.suitableWhen.isBlank() || length(reply.suitableWhen)>200 || reply.cost.isBlank() || length(reply.cost)>200) errors.add("适用条件及代价须为 1–200 字")
                if(listOf(reply.branches.positive,reply.branches.ambiguous,reply.branches.rejection).any { it.isBlank() || length(it)>300 }) errors.add("须有积极、含糊、拒绝三种反馈的后续分支")
            }
            if(reply.score!=null && (!reply.score.isFinite() || reply.score !in 0.0..1.0)) errors.add("评分须为 0–1 或 null")
        }
        if(listOf(batch.analysis.facts,batch.analysis.inferences,batch.analysis.unknowns).any { list -> list.size>20 || list.any { length(it)>500 } }) errors.add("分析材料过长，请精简")
        return ValidationResult(errors.isEmpty(),errors.distinct())
    }
    companion object {
        fun normalize(text:String):String = Normalizer.normalize(text,Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("[\\p{P}\\p{Z}\\s]+"),"")
        private fun length(text:String)=text.codePointCount(0,text.length)
    }
}
