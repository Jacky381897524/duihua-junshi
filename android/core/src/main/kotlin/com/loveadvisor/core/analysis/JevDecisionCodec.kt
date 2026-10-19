package com.loveadvisor.core.analysis
import com.google.gson.*
import com.loveadvisor.core.model.*
import kotlin.math.roundToInt
object JevDecisionCodec {
    val intents=linkedMapOf("confirm_you_care" to "确认你是否在意","vent_anger" to "表达不满或委屈","request_action" to "希望具体行动","seek_explanation" to "希望解释原因","casual_chat" to "轻松交流与分享","close_topic" to "结束当前话题")
    val needs=linkedMapOf("apology" to "真诚道歉","action" to "具体行动","explanation" to "事实解释","care" to "被关心与倾听","nothing" to "无需进一步回应")
    val actions=linkedMapOf("check_history" to "先核对已有记录","apologize" to "承认已知问题并道歉","give_commitment" to "给出可兑现的安排","explain" to "简短解释已知事实","acknowledge" to "先接住具体内容","say_less" to "少说，尊重边界","make_plan" to "确认具体计划")
    private fun number(e:JsonElement?,max:Double):Double?=runCatching { e?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.takeIf { it.isFinite() && it in 0.0..max } }.getOrNull()
    fun decode(raw:String,snapshot:ChatSnapshot,source:String):MessageJudgment {
        val root=JsonParser.parseString(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).asJsonObject
        val a=root.getAsJsonObject("answers") ?: root
        require(listOf("literal_question","true_intent","she_needs","best_action","danger_level").any { a.has(it) }) { "判断模型未返回可识别的判断字段" }
        fun obj(key:String)=a.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        fun options(key:String,labels:Map<String,String>):List<DecisionOption> {
            val answer=obj(key) ?: return emptyList()
            val probs=answer.get("probabilities")?.takeIf { it.isJsonObject }?.asJsonObject
            val entries=probs?.entrySet()?.toList().orEmpty()
            val valid=entries.isNotEmpty() && entries.all { it.key in labels && number(it.value,1.0)!=null } && kotlin.math.abs(entries.sumOf { number(it.value,1.0) ?: 0.0 }-1.0)<.03
            if(valid)return entries.map { DecisionOption(it.key,labels.getValue(it.key),number(it.value,1.0)) }.sortedByDescending { it.weight }.take(3)
            val chosen=runCatching { answer.get("choice")?.asString }.getOrNull()
            return chosen?.takeIf { it in labels }?.let { listOf(DecisionOption(it,labels.getValue(it),null)) }.orEmpty()
        }
        val level=number(obj("danger_level")?.get("score"),9.0)?.let { (it.roundToInt()+1).coerceIn(1,9) }
        val target=snapshot.messages.last();require(target.speaker==Speaker.OTHER)
        val result=MessageJudgment(target.id,number(obj("literal_question")?.get("noul"),1.0),options("true_intent",intents),options("she_needs",needs),options("best_action",actions),level,snapshot.messages.takeLast(3),source)
        require(result.literal!=null || result.intents.isNotEmpty() || result.needs.isNotEmpty() || result.actions.isNotEmpty() || result.level!=null) { "模型没有返回有效判断" }
        return result
    }
}
