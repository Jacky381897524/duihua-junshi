"""Preserve v1.3 seven judgement questions; replace only org.json builders with Gson."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
source=(root/'reference/jev-chat-jarvis-v1.3/app/src/main/java/com/jev/probe/jev/JevQuestions.kt').read_text(encoding='utf-8')
begin=source.index('object JevQuestions {')
end=source.index('    /**\n     * Build Jev state')
body=source[begin:end].replace('JSONObject','JsonObject').replace('JSONArray','JsonArray').replace('a.put(it)','a.add(it)')
helper='''
    private fun JsonObject.put(key:String,value:Any):JsonObject {
        when(value) {
            is JsonElement -> add(key,value)
            is String -> addProperty(key,value)
            is Number -> addProperty(key,value)
            is Boolean -> addProperty(key,value)
            else -> error("unsupported JSON value")
        }
        return this
    }
}
'''
target=root/'android/core/src/main/kotlin/com/loveadvisor/core/api/JevQuestions.kt'
target.write_text('package com.loveadvisor.core.api\nimport com.google.gson.*\n// Jev v1.3 MIT, commit 23b5ca5. Original wording preserved.\n'+body+helper,encoding='utf-8')
