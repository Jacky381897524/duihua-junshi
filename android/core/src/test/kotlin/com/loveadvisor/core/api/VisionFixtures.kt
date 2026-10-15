package com.loveadvisor.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonParser

/** Valid wire fixtures for legacy API tests; geometry rejection has separate literal fixtures. */
object VisionFixtures {
    fun content(raw:String):String {
        val data=JsonParser.parseString(raw).asJsonObject
        data.addProperty("reliable",true)
        data.add("chatBounds",JsonParser.parseString("[0,100,1000,900]"))
        data["messages"]?.asJsonArray?.forEachIndexed { i,row ->
            val record=row.asJsonObject;val right=record["speaker"]?.asString=="ME"
            val bounds=JsonArray().apply { listOf(if(right)650 else 130,200+i*100,if(right)870 else 500,250+i*100).forEach { add(it) } }
            record.add("bubble",bounds);record.add("textBounds",bounds.deepCopy())
        }
        return data.toString()
    }
}
