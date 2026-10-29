package com.loveadvisor.core.diagnostics

import com.google.gson.JsonParser

/** Projects only bounded provider-reported token counters; never returns response content. */
object TokenUsage {
    fun counters(body:String):Map<LogMetric,Long> {
        if(body.length>2*1024*1024)return emptyMap()
        return runCatching {
            val usage=JsonParser.parseString(body).asJsonObject.getAsJsonObject("usage") ?: return emptyMap()
            fun number(value:com.google.gson.JsonElement?):Long?=runCatching {
                if(value?.isJsonPrimitive==true && value.asJsonPrimitive.isNumber)value.asBigDecimal.longValueExact().takeIf { it in 0..1_000_000_000L } else null
            }.getOrNull()
            buildMap {
                number(usage["prompt_tokens"] ?: usage["input_tokens"])?.let { put(LogMetric.INPUT_TOKENS,it) }
                number(usage["completion_tokens"] ?: usage["output_tokens"])?.let { put(LogMetric.OUTPUT_TOKENS,it) }
                number(usage.getAsJsonObject("prompt_tokens_details")?.get("cached_tokens"))?.let { put(LogMetric.CACHED_INPUT_TOKENS,it) }
            }
        }.getOrDefault(emptyMap())
    }
}
