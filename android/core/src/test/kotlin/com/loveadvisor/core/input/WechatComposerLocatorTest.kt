package com.loveadvisor.core.input

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class WechatComposerLocatorTest {
    private class Screen(val w:Int=360,val h:Int=780,val light:Boolean=false) {
        val background=if(light)0xffededed.toInt() else 0xff111111.toInt()
        val toolbar=if(light)0xfff7f7f7.toInt() else 0xff1e1e1e.toInt()
        val field=if(light)0xffffffff.toInt() else 0xff292929.toInt()
        val ink=if(light)0xff333333.toInt() else 0xffdddddd.toInt()
        val pixels=IntArray(w*h) { background }
        val box=RectI(w*12/100,h-w*16/100,w*79/100,h-w*7/100)
        fun rect(r:RectI,color:Int) { for(y in r.top until r.bottom)for(x in r.left until r.right)pixels[y*w+x]=color }
        init {
            rect(RectI(0,box.top-8,w,h),toolbar)
            rect(box,field)
            for(cx in listOf(w*6/100,w*85/100,w*95/100)) {
                val cy=(box.top+box.bottom)/2;val radius=w*3/100
                for(y in cy-radius..cy+radius)for(x in cx-radius..cx+radius) {
                    val d=(x-cx)*(x-cx)+(y-cy)*(y-cy)
                    if(d in (radius-2)*(radius-2)..radius*radius)pixels[y*w+x]=ink
                }
            }
        }
        fun locate(pkg:String="com.tencent.mm")=WechatComposerLocator.locate(pkg,w,h) { x,y -> pixels[y*w+x] }
    }
    @Test fun visibleEmptyEditorIsFoundAtDifferentSizesAndThemes() {
        for(w in listOf(360,720,1080))for(light in listOf(false,true)) {
            val image=Screen(w,w*13/6,light)
            val r=image.locate()
            assertNotNull("visible editor $w light=$light",r)
            assertTrue(r!!.left>=image.box.left && r.right<=image.box.right)
            assertTrue(r.top>=image.box.top && r.bottom<=image.box.bottom)
            assertTrue(r.width>image.w*.5)
        }
    }
    @Test fun toolbarWithoutSideControlsCannotAuthorizeATap() {
        val image=Screen()
        image.rect(RectI(0,image.box.top-8,image.box.left-1,image.h),image.toolbar)
        assertNull(image.locate())
    }
    @Test fun voiceHoldButtonAndKeyboardAreaAreNotTextEditors() {
        val voice=Screen()
        val cy=(voice.box.top+voice.box.bottom)/2
        voice.rect(RectI(voice.w*35/100,cy-7,voice.w*62/100,cy+7),voice.ink)
        assertNull(voice.locate())
        val keyboard=Screen()
        keyboard.rect(RectI(0,keyboard.box.bottom-2,keyboard.w,keyboard.h),keyboard.field)
        assertNull(keyboard.locate())
    }
    @Test fun arbitraryFlatScreensAndOtherPlatformsAreRejected() {
        assertNull(WechatComposerLocator.locate("com.tencent.mm",360,780) { _,_->0xff222222.toInt() })
        assertNull(Screen().locate("com.tencent.mobileqq"))
        assertNull(Screen().locate("com.ss.android.ugc.aweme"))
    }
}
