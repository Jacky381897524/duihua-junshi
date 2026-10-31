package com.loveadvisor.core.capture

import com.loveadvisor.core.advisor.NicknameText
import com.loveadvisor.core.model.RectI

/** A local, editable nickname suggestion. This is never identity verification. */
object ReplyStyleHeader {
    fun crop(packageName:String,viewport:RectI,statusBarBottom:Int,density:Float):RectI? {
        if(!ChatApps.supported(packageName) || viewport.width<=0 || viewport.height<=0)return null
        val left=when(packageName) { ChatApps.WECHAT -> .25;ChatApps.QQ -> .12;else -> .28 }
        val top=maxOf(viewport.top,statusBarBottom).coerceAtMost(viewport.bottom)
        val bottom=minOf(viewport.bottom,top+(56*density).toInt())
        return RectI(viewport.left+(viewport.width*left).toInt(),top,viewport.left+(viewport.width*.84).toInt(),bottom).takeIf { it.width>0 && it.height>0 }
    }
    fun title(boxes:List<OcrTextBox>):String?=boxes.singleOrNull()?.text?.trim()?.takeIf {
        NicknameText.valid(it) && '\n' !in it && '\r' !in it && !TextSceneParser.isUi(it) && it !in setOf("消息","联系人","通讯录","好友","设置")
    }
    /** Match only a unique existing cached title, never guess from similar OCR text. */
    fun cachedTitle(boxes:List<OcrTextBox>,titles:List<String>,cropWidth:Int,cropHeight:Int):String? {
        if(cropWidth<=0 || cropHeight<=0)return null
        fun ambiguousKey(value:String)=NicknameText.suggestionKey(value).filterNot { it.isWhitespace() }.lowercase(java.util.Locale.ROOT)
        val eligible=titles.filter { NicknameText.valid(it) && !TextSceneParser.isUi(it) && it !in setOf("消息","联系人","通讯录","好友","设置") }
        val seen=boxes.filter { box ->
            val b=box.bounds;val x=(b.left+b.right)/2.0/cropWidth;val y=(b.top+b.bottom)/2.0/cropHeight
            x in .18.. .68 && y in .05.. .9
        }.flatMap { it.text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList() }.map(NicknameText::canonical).toSet()
        return eligible.filter { name -> NicknameText.canonical(name) in seen && eligible.count { ambiguousKey(it)==ambiguousKey(name) }==1 }.singleOrNull()
    }
}
