package com.loveadvisor.core.capture

import com.loveadvisor.core.advisor.NicknameText

/** Optional raw nickname for style lookup only; never changes the fill/session identity. */
object NativeReplyStyleTitle {
    fun read(window:WindowSnapshot?):String? {
        if(window==null || window.packageName !in setOf(ChatApps.QQ,ChatApps.WECHAT))return null
        val v=window.viewport
        if(!window.nodes.any { it.editable && it.bounds.top>v.top+v.height*.55 })return null
        if(window.packageName==ChatApps.WECHAT) {
            val name=window.windowClassName.orEmpty()
            if(listOf(".profile.",".webview.","appbrand").any { name.contains(it,true) })return null
            if(WeChatPageClassifier.classify(window) !in setOf(WeChatPageKind.CHAT_OR_UNKNOWN,WeChatPageKind.GROUP))return null
            // The scene parser's wider header search also reaches the first bubble.
            // Only one distinct title entirely inside the top navigation band is safe here.
            return window.nodes.filter {
                val r=it.bounds;val t=it.text.orEmpty()
                !it.editable && NicknameText.valid(t) && !TextSceneParser.isUi(t) &&
                    r.top>=v.top+v.height*.025 && r.bottom<v.top+v.height*.105 &&
                    (r.left+r.right)/2 in (v.left+v.width*.25).toInt()..(v.left+v.width*.75).toInt()
            }.mapNotNull { it.text }.distinct().singleOrNull()
        }
        return window.nodes.filter {
            it.id=="com.tencent.mobileqq:id/3kc" && !it.editable && NicknameText.valid(it.text.orEmpty()) &&
                it.bounds.top>=v.top && it.bounds.bottom<v.top+v.height*.15 && it.bounds.left>=v.left+v.width*.1
        }.singleOrNull()?.text
    }
}
