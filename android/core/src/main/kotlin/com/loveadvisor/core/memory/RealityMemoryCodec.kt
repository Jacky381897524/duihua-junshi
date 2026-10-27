package com.loveadvisor.core.memory

import com.google.gson.Gson
import com.google.gson.JsonParser

/** Missing policy metadata means an older classifier, not the Kotlin constructor's current default. */
object RealityMemoryCodec {
    fun decode(raw:String):RealityMemoryState {
        val json=JsonParser.parseString(raw).asJsonObject
        val state=Gson().fromJson(json,RealityMemoryState::class.java)
        require(state.schema==1) { "记忆数据版本不受支持" }
        return state.copy(learningPolicy=if(json.has("learningPolicy"))state.learningPolicy else 0)
    }
}
