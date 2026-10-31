package com.loveadvisor.android.input

import android.graphics.Bitmap
import com.loveadvisor.core.model.RectI
import java.security.MessageDigest

/** Ephemeral title-strip fingerprint; never saved or sent to a model. */
data class VisualFillReference(val windowId:Int,val generation:Long,val stamp:String,val composer:RectI?,val footerStamp:String?=null,val viewport:RectI?=null,val keyboardButton:RectI?=null,val conversationStamp:String?=null) {
    companion object {
        fun footerStamp(bitmap:Bitmap):String? {
            val height=(bitmap.width*.2).toInt().coerceAtMost(bitmap.height)
            return if(height<=0)null else areaStamp(bitmap,0,bitmap.height-height,bitmap.width,height)?.let { "${bitmap.width}x${bitmap.height}:$it" }
        }
        fun stamp(bitmap:Bitmap):String? {
            val left=(bitmap.width*.18).toInt();val top=(bitmap.width*.11).toInt()
            val width=(bitmap.width*.64).toInt();val height=(bitmap.width*.09).toInt()
            if(width<=0 || height<=0 || top+height>bitmap.height)return null
            return areaStamp(bitmap,left,top,width,height)
        }
        /** Fingerprint visible chat pixels only; never retain or transmit the image. */
        fun conversationStamp(bitmap:Bitmap):String? {
            val left=(bitmap.width*.05).toInt();val top=(bitmap.width*.25).toInt()
            val width=(bitmap.width*.9).toInt();val bottom=bitmap.height-(bitmap.width*.25).toInt()
            if(width<=0 || bottom<=top || left+width>bitmap.width)return null
            return areaStamp(bitmap,left,top,width,bottom-top,requireDetail=true)
        }
        private fun areaStamp(bitmap:Bitmap,left:Int,top:Int,width:Int,height:Int,requireDetail:Boolean=false):String? {
            val crop=Bitmap.createBitmap(bitmap,left,top,width,height)
            val scaled=Bitmap.createScaledBitmap(crop,96,24,true)
            val pixels=IntArray(96*24);scaled.getPixels(pixels,0,96,0,0,96,24)
            if(scaled!==crop)scaled.recycle();if(crop!==bitmap)crop.recycle()
            if(requireDetail && !com.loveadvisor.core.capture.ConversationFingerprint.hasDetail(pixels))return null
            val bytes=java.nio.ByteBuffer.allocate(pixels.size*4).apply { pixels.forEach { putInt(it) } }.array()
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
