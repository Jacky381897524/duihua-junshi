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
    /** Window screenshots may already exclude system bars; inspect both possible toolbar origins. */
    fun captureCrop(packageName:String,viewport:RectI,statusBarBottom:Int,density:Float):RectI? =
        crop(packageName,viewport,statusBarBottom,density)?.let {
            if(packageName==ChatApps.WECHAT)it.copy(left=viewport.left+(viewport.width*.12).toInt(),right=viewport.left+(viewport.width*.90).toInt(),top=viewport.top)
            else it.copy(top=viewport.top)
        }

    /** Map a window-space toolbar with one pixel scale, even during keyboard animation. */
    fun bitmapCrop(crop:RectI,viewport:RectI,width:Int,height:Int):RectI? {
        if(width<=0 || height<=0 || viewport.width<=0 || viewport.height<=0)return null
        val scale=width.toDouble()/viewport.width
        val left=((crop.left-viewport.left)*scale).toInt().coerceIn(0,width)
        val top=((crop.top-viewport.top)*scale).toInt().coerceIn(0,height)
        val right=((crop.right-viewport.left)*scale).toInt().coerceIn(0,width)
        val bottom=((crop.bottom-viewport.top)*scale).toInt().coerceIn(0,height)
        return RectI(left,top,right,bottom).takeIf { it.width>0 && it.height>0 }
    }

    fun fingerprintBand(boxes:List<OcrTextBox>,width:Int,height:Int,expectedTitle:String?=null):RectI? {
        fun key(s:String)=NicknameText.canonical(s).filterNot { it.isWhitespace() }
        val rows=toolbarRows(boxes,width,height)
        val row=if(expectedTitle==null)rows.firstOrNull() else rows.singleOrNull { key(it.joinToString("") { box->box.text })==key(expectedTitle) }
        if(row==null)return null
        if(row.zipWithNext().any { (a,b)->a.bounds.right>b.bounds.left })return null
        val top=row.minOf { it.bounds.top };val bottom=row.maxOf { it.bounds.bottom }
        val margin=maxOf(2,(bottom-top)/4)
        return RectI(0,(top-margin).coerceAtLeast(0),width,(bottom+margin).coerceAtMost(height))
    }
    fun title(boxes:List<OcrTextBox>):String?=boxes.singleOrNull()?.text?.trim()?.takeIf {
        NicknameText.valid(it) && '\n' !in it && '\r' !in it && !TextSceneParser.isUi(it) && it !in setOf("消息","联系人","通讯录","好友","设置")
    }
    private fun toolbarRows(boxes:List<OcrTextBox>,width:Int,height:Int):List<List<OcrTextBox>> {
        if(width<=0 || height<=0)return emptyList()
        val central=boxes.filter { b -> val r=b.bounds;val x=(r.left+r.right)/2.0/width
            x in .12.. .82 && r.top>=0 && r.bottom<=height && !TextSceneParser.isUi(b.text) && b.text.isNotBlank() &&
                !Regex("^(?:[2345]G|LTE|VoLTE|HD|[0-9]{1,3}%)$",RegexOption.IGNORE_CASE).matches(b.text.trim())
        }.sortedBy { it.bounds.top }
        val rows=mutableListOf<MutableList<OcrTextBox>>()
        central.forEach { box ->
            val row=rows.lastOrNull()
            if(row!=null && minOf(row.first().bounds.bottom,box.bounds.bottom)-maxOf(row.first().bounds.top,box.bounds.top)>minOf(row.first().bounds.height,box.bounds.height)*.5)row.add(box)
            else rows.add(mutableListOf(box))
        }
        return rows.map { it.sortedBy { b -> b.bounds.left } }
    }
    fun toolbarTitle(boxes:List<OcrTextBox>,width:Int,height:Int):String? {
        val rows=toolbarRows(boxes,width,height)
        val row=rows.firstOrNull() ?: return null
        // A word touching a crop edge may be only part of a long remark.
        if(row.any { it.bounds.left<=1 || it.bounds.right>=width-1 })return null
        if(row.zipWithNext().any { (a,b) -> a.bounds.right>b.bounds.left })return null
        return title(listOf(OcrTextBox(row.joinToString(" ") { it.text.trim() },row.first().bounds)))
    }
    /** Match only a unique existing cached title, never guess from similar OCR text. */
    fun cachedTitle(boxes:List<OcrTextBox>,titles:List<String>,cropWidth:Int,cropHeight:Int):String? {
        if(cropWidth<=0 || cropHeight<=0)return null
        fun ambiguousKey(value:String)=NicknameText.suggestionKey(value).filterNot { it.isWhitespace() }.lowercase(java.util.Locale.ROOT)
        val eligible=titles.distinct().filter { NicknameText.valid(it) && !TextSceneParser.isUi(it) && it !in setOf("消息","联系人","通讯录","好友","设置") }
        // OCR may insert or omit a space between Latin and Chinese text. Ignore
        // only whitespace for comparison, and reject collisions instead of guessing.
        fun matchKey(value:String)=NicknameText.canonical(value).filterNot { it.isWhitespace() }
        val seen=(boxes.filter { box ->
            val b=box.bounds;val x=(b.left+b.right)/2.0/cropWidth;val y=(b.top+b.bottom)/2.0/cropHeight
            x in .18.. .68 && y in .05.. .9 && b.left>1 && b.right<cropWidth-1
        }.flatMap { it.text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList() }+
            toolbarRows(boxes,cropWidth,cropHeight).filter { row -> row.none { it.bounds.left<=1 || it.bounds.right>=cropWidth-1 } && row.zipWithNext().none { (a,b) -> a.bounds.right>b.bounds.left } }.map { row -> row.joinToString("") { it.text } }).map(::matchKey).toSet()
        return eligible.filter { name -> matchKey(name) in seen && eligible.count { ambiguousKey(it)==ambiguousKey(name) }==1 }.singleOrNull()
    }
}
