package com.loveadvisor.android.input

import android.graphics.Bitmap
import com.loveadvisor.core.model.RectI
import java.security.MessageDigest

/** Ephemeral title-strip fingerprint; never saved or sent to a model. */
data class VisualFillReference(val windowId:Int,val generation:Long,val stamp:String,val composer:RectI?,val footerStamp:String?=null,val viewport:RectI?=null,val keyboardButton:RectI?=null) {
    companion object {
        fun footerStamp(bitmap:Bitmap):String? {
            val height=(bitmap.width*.2).toInt().coerceAtMost(bitmap.height)
            return if(height<=0)null else "${bitmap.width}x${bitmap.height}:"+areaStamp(bitmap,0,bitmap.height-height,bitmap.width,height)
        }
        fun stamp(bitmap:Bitmap):String? {
            val left=(bitmap.width*.18).toInt();val top=(bitmap.width*.11).toInt()
            val width=(bitmap.width*.64).toInt();val height=(bitmap.width*.09).toInt()
            if(width<=0 || height<=0 || top+height>bitmap.height)return null
            return areaStamp(bitmap,left,top,width,height)
        }
        private fun areaStamp(bitmap:Bitmap,left:Int,top:Int,width:Int,height:Int):String {
            val crop=Bitmap.createBitmap(bitmap,left,top,width,height)
            val scaled=Bitmap.createScaledBitmap(crop,96,24,true)
            val pixels=IntArray(96*24);scaled.getPixels(pixels,0,96,0,0,96,24)
            if(scaled!==crop)scaled.recycle();if(crop!==bitmap)crop.recycle()
            val bytes=java.nio.ByteBuffer.allocate(pixels.size*4).apply { pixels.forEach { putInt(it) } }.array()
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
