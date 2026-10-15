package com.loveadvisor.android.input
import android.content.*
import android.widget.Toast
object ClipboardController {
    fun copy(context:Context,text:String,notify:Boolean=true) { val clip=ClipData.newPlainText("候选回复",text); if(android.os.Build.VERSION.SDK_INT>=33) clip.description.extras=android.os.PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true) }; (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip); if(notify)Toast.makeText(context,"已复制，请核对后粘贴",Toast.LENGTH_SHORT).show() }
}
