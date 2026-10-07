package com.loveadvisor.android.settings
import android.graphics.*
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Synthetic fixture only: never captures the user's screen or conversation. */
object ConnectionTestImage {
    fun dataUrl():String {
        val image=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(image); canvas.drawColor(Color.rgb(237,237,237))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color=Color.rgb(23,34,54); paint.textSize=24f
            canvas.drawText("‹",16f,65f,paint); canvas.drawText("连接测试",120f,65f,paint)
            paint.color=Color.WHITE; canvas.drawRoundRect(RectF(60f,145f,174f,199f),9f,9f,paint)
            paint.color=Color.rgb(149,236,105); canvas.drawRoundRect(RectF(186f,235f,300f,289f),9f,9f,paint)
            paint.color=Color.rgb(92,130,185); canvas.drawRect(12f,145f,48f,181f,paint); canvas.drawRect(312f,235f,348f,271f,paint)
            paint.color=Color.BLACK; paint.textSize=25f; canvas.drawText("你好",77f,181f,paint); canvas.drawText("收到",204f,271f,paint)
            paint.color=Color.WHITE; canvas.drawRect(0f,565f,360f,640f,paint)
            paint.color=Color.LTGRAY; canvas.drawRoundRect(RectF(42f,582f,278f,625f),6f,6f,paint)
            paint.color=Color.rgb(23,34,54); paint.textSize=20f; canvas.drawText("发送",292f,611f,paint)
            val bytes=ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.PNG,100,bytes)
            return "data:image/png;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)
        } finally { image.recycle() }
    }
}
