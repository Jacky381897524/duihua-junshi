package com.loveadvisor.android.knowledge
import android.content.Context
import com.google.gson.JsonParser
import com.loveadvisor.core.knowledge.*
class AssetKnowledgeStore(private val context:Context) {
    val documents:List<KnowledgeDocument> by lazy {
        val root=JsonParser.parseString(context.assets.open("knowledge/index.json").bufferedReader().use { it.readText() })
        val rows=if(root.isJsonArray) root.asJsonArray else root.asJsonObject.getAsJsonArray("documents")
        rows.map { row ->
            val o=row.asJsonObject; val path=o["path"].asString
            KnowledgeDocument(o["id"].asString,o["title"].asString,path,o.getAsJsonArray("tags").map { it.asString },o["source"].asString,context.assets.open(path).bufferedReader().use { it.readText() })
        }
    }
}
