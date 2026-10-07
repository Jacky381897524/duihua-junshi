package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
enum class WeChatPageKind { CHAT_OR_UNKNOWN, MINI_PROGRAM, WEB_PAGE, HOME, CONTACT_PROFILE, GROUP, INVALID }
object WeChatPageClassifier {
    fun visibleNodes(window:WindowSnapshot)=window.nodes.filter { val r=it.bounds; val v=window.viewport; r.width>0 && r.height>0 && r.right>v.left && r.left<v.right && r.bottom>v.top && r.top<v.bottom }
    fun classify(window:WindowSnapshot):WeChatPageKind {
        val v=window.viewport
        if(window.packageName!="com.tencent.mm" || v.width<=0 || v.height<=0) return WeChatPageKind.INVALID
        val nodes=visibleNodes(window)
        val names=listOfNotNull(window.windowClassName)+nodes.map { it.className }
        if(names.any { it.contains("appbrand",ignoreCase=true) }) return WeChatPageKind.MINI_PROGRAM
        if(window.windowClassName.orEmpty().contains(".webview.",ignoreCase=true) || nodes.any { it.className.contains("WebView",ignoreCase=true) && it.bounds.width>=v.width*.65 && it.bounds.height>=v.height*.4 }) return WeChatPageKind.WEB_PAGE
        if(TextSceneParser.kind(TextSceneParser.title(nodes,v))==ChatKind.GROUP) return WeChatPageKind.GROUP
        val input=nodes.any { (it.editable || it.className.contains("EditText") || it.text.orEmpty().replace(" ","").trim()=="按住说话") && it.bounds.top>=v.top+v.height*.55 && !it.text.orEmpty().contains("搜索") }
        if(!input) {
            val tabs=nodes.filter { it.text?.trim() in setOf("微信","通讯录","发现","我") && it.bounds.top>=v.top+v.height*.8 && it.bounds.bottom<=v.bottom && it.bounds.width<=v.width*.23 }
            if(tabs.any { a -> tabs.filter { kotlin.math.abs(it.bounds.top-a.bounds.top)<v.height*.025 }.map { it.text!!.trim() }.distinct().size>=3 }) return WeChatPageKind.HOME
            val profileAction=nodes.any { it.text?.trim()=="发消息" && (it.className.contains("Button") || it.contentDescription.orEmpty().contains("发消息")) && it.bounds.width>v.width*.4 }
            if(window.windowClassName.orEmpty().contains(".profile.",ignoreCase=true) || (profileAction && nodes.any { it.text?.trim()=="微信号" })) return WeChatPageKind.CONTACT_PROFILE
        }
        return WeChatPageKind.CHAT_OR_UNKNOWN
    }
}
