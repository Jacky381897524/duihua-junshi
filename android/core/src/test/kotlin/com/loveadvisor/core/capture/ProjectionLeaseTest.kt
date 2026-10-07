package com.loveadvisor.core.capture
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test
class ProjectionLeaseTest {
    private fun window(pkg:String="com.tencent.mm",id:Int=7)=WindowSnapshot(pkg,id,RectI(0,0,1080,2400),emptyList())
    @Test fun sameWindowIdInAnotherPackageCannotAcceptLateOcr() {
        val lease=ProjectionLease("com.tencent.mm",7,1080,2400)
        assertTrue(lease.accepts(window())); assertFalse(lease.accepts(window("com.tencent.mobileqq")))
        assertFalse(lease.accepts(window(id=8))); assertFalse(lease.accepts(null))
    }
    @Test fun rotationOrCancellationPermanentlyRejectsEvenOriginalWindow() {
        val rotated=ProjectionLease("com.tencent.mm",7,1080,2400)
        assertTrue(rotated.resize(1080,2400)); assertFalse(rotated.resize(2400,1080)); assertFalse(rotated.accepts(window()))
        val stopped=ProjectionLease("com.tencent.mm",7,1080,2400); stopped.cancel(); assertFalse(stopped.accepts(window()))
    }
}
