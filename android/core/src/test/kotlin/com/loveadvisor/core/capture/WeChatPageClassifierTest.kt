package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class WeChatPageClassifierTest {
    private val frame=RectI(0,0,1000,2000)
    private fun node(text:String,bounds:RectI,editable:Boolean=false)=NodeRecord(null,text,if(editable) "android.widget.EditText" else "android.widget.TextView",editable,bounds)
    private val title=node("小雨",RectI(400,80,600,145))
    private val input=node("",RectI(120,1700,800,1800),true)
    private val message=node("今天怎么样",RectI(130,400,600,500))
    private fun window(nodes:List<NodeRecord>,clazz:String?=null)=WindowSnapshot("com.tencent.mm",42,frame,nodes,windowClassName=clazz)
    @Test fun miniProgramActivityCannotBeReadAsAChatEvenWithComposerAndMessages() {
        val w=window(listOf(title,input,message),"com.tencent.mm.plugin.appbrand.ui.AppBrandUI")
        assertEquals(WeChatPageKind.MINI_PROGRAM,WeChatPageClassifier.classify(w))
        assertEquals(CaptureResult.NotChat,AutomaticWeChatParser().extract(w))
    }
    @Test fun largeWebViewAndItsCommentFieldAreNotWechatChat() {
        val w=window(listOf(title,input,message,NodeRecord(null,null,"android.webkit.WebView",false,RectI(0,180,1000,1700))))
        assertEquals(WeChatPageKind.WEB_PAGE,WeChatPageClassifier.classify(w))
        assertEquals(CaptureResult.NotChat,AutomaticWeChatParser().extract(w))
    }
    @Test fun sayingWechatIdDoesNotMakeMissingComposerAContactProfile() {
        val w=window(listOf(title,node("微信号",RectI(130,400,600,500))))
        assertTrue(AutomaticWeChatParser().extract(w) is CaptureResult.NeedsImage)
    }
    @Test fun offscreenCachedHomeTabsDoNotRejectCurrentChat() {
        val cached=listOf("微信","通讯录","发现","我").mapIndexed { i,t -> node(t,RectI(i*250,2200,i*250+180,2300)) }
        assertTrue(AutomaticWeChatParser().extract(window(listOf(title,message)+cached)) is CaptureResult.NeedsImage)
    }
    @Test fun wordsInDifferentMessageRowsCannotBeTreatedAsHomeNavigation() {
        val rows=listOf("微信","发现","我").mapIndexed { i,t -> node(t,RectI(130,1650+i*90,400,1700+i*90)) }
        assertTrue(AutomaticWeChatParser().extract(window(listOf(title)+rows)) is CaptureResult.NeedsImage)
    }
}
