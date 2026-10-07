package com.loveadvisor.android.glass

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.webkit.*
import android.widget.FrameLayout
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Local-only WebGL. No JS/native bridge, remote requests, chat parsing or model calls. */
class GlassBackgroundView(context:Context):FrameLayout(context) {
    private val origin="https://appassets.androidplatform.net"
    private val encoder=Executors.newSingleThreadExecutor { r->Thread(r,"glass-frame-encoder") }
    private val lock=Any(); private var pending:Pair<Bitmap,Long>?=null; private var encoding=false
    @Volatile private var frameEpoch=0L
    @Volatile private var released=false
    private var ready=false; private var latestEncoded:String?=null
    private val web=WebView(context)
    init {
        web.setBackgroundColor(Color.TRANSPARENT); web.isFocusable=false; web.isClickable=false
        web.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        web.settings.apply { javaScriptEnabled=true; allowFileAccess=false; allowContentAccess=false; blockNetworkLoads=true; domStorageEnabled=false; setSupportZoom(false); cacheMode=WebSettings.LOAD_NO_CACHE }
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                val url=request.url; val path=url.path.orEmpty()
                if(url.scheme!="https" || url.host!="appassets.androidplatform.net" || !path.startsWith("/liquid-glass/") || path.contains("..")) return denied()
                val mime=if(path.endsWith(".js")) "text/javascript" else if(path.endsWith(".html")) "text/html" else "text/plain"
                return runCatching { WebResourceResponse(mime,"UTF-8",context.assets.open(path.removePrefix("/"))) }.getOrElse { denied() }
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(released || url!="$origin/liquid-glass/background.html") return
                // The module can finish after onPageFinished; each frame retries until it defines the hook.
                ready=true; val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
                web.evaluateJavascript("window.setAppearance?.($dark)",null); latestEncoded?.let { submit(it) }
            }
        }
        addView(web,LayoutParams(-1,-1)); web.loadUrl("$origin/liquid-glass/background.html")
    }
    private fun denied()=WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    /** Copies immediately so the caller keeps ownership of its bitmap. At most one pending frame. */
    fun updateFrame(bitmap:Bitmap) {
        if(released || bitmap.isRecycled) return
        val epoch=frameEpoch
        val copy=bitmap.copy(Bitmap.Config.ARGB_8888,false) ?: return
        synchronized(lock) { if(released || epoch!=frameEpoch) { copy.recycle();return }; pending?.first?.recycle();pending=copy to epoch;if(encoding)return;encoding=true }
        encoder.execute {
            while(true) {
                val delivery=synchronized(lock) { val next=pending;pending=null;if(next==null)encoding=false;next } ?: break
                val frame=delivery.first
                try {
                    val out=ByteArrayOutputStream(); frame.compress(Bitmap.CompressFormat.JPEG,82,out)
                    val encoded=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
                    post { if(!released && delivery.second==frameEpoch) { latestEncoded=encoded;if(ready)submit(encoded) } }
                } finally { frame.recycle() }
            }
        }
    }
    fun clearFrame() {
        synchronized(lock) { frameEpoch++;pending?.first?.recycle();pending=null }
        latestEncoded=null;if(!released)web.evaluateJavascript("window.clearGlassFrame?.()",null)
    }
    private fun submit(encoded:String) {
        val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        web.evaluateJavascript("window.setAppearance?.($dark);window.acceptFrame?.(${Gson().toJson(encoded)})",null)
    }
    fun release() {
        synchronized(lock) { if(released)return;released=true;frameEpoch++;pending?.first?.recycle();pending=null }
        latestEncoded=null; encoder.shutdownNow();web.evaluateJavascript("window.releaseGlass?.()",null);web.stopLoading();removeView(web);web.destroy()
    }
}
