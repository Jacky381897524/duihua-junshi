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
}
