package com.loveadvisor.core.advisor

import com.google.gson.JsonObject

/** Preserve the decision meaning and expose uncertainty when a valid distribution is weak. */
object IntentSummary {
    fun describe(answer:JsonObject,labels:Map<String,String>):String {
        val choice=answer["choice"]?.asString ?: error("intent")
        val text=labels[choice] ?: error("intent")
        val distribution=answer["probabilities"]?.takeIf { it.isJsonObject }?.asJsonObject
        val values=runCatching { distribution?.entrySet()?.associate { it.key to it.value.asDouble } }.getOrNull()
        if(values!=null && values.keys==labels.keys && values.values.all { it.isFinite() && it in 0.0..1.0 } && kotlin.math.abs(values.values.sum()-1)<.03) {
            val sorted=values.values.sortedDescending()
            if(values.getValue(choice)<.5 || sorted[0]-sorted[1]<.1)return "信息不足，暂不能确定对方意图"
        }
        return text
    }
}
