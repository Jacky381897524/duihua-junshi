package com.loveadvisor.android.integration
import android.graphics.Bitmap
import android.graphics.Color
import com.loveadvisor.android.input.VisualFillReference
import org.junit.Assert.*
import org.junit.Test
class VisualFillReferenceTest {
    @Test fun keyboardAndMessageChangesDoNotChangeTitleReferenceButDifferentTitleDoes() {
        val image=Bitmap.createBitmap(1080,2344,Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        for(x in 420..580)for(y in 140..175)image.setPixel(x,y,Color.BLACK)
        val original=VisualFillReference.stamp(image)
        val footer=VisualFillReference.footerStamp(image)
        assertNotNull(original)
        for(x in 0..1079)for(y in 1700..2343)image.setPixel(x,y,Color.DKGRAY)
        assertEquals(original,VisualFillReference.stamp(image))
        assertNotEquals(footer,VisualFillReference.footerStamp(image))
        for(x in 400..460)for(y in 140..175)image.setPixel(x,y,Color.BLACK)
        assertNotEquals(original,VisualFillReference.stamp(image))
        image.recycle()
    }
}
