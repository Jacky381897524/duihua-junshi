package com.loveadvisor.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class ProcessExitSummaryTest {
    private fun summary(vararg rows:ProcessExitRecord)=ProcessExitSummary.describe(rows.toList()) { "time-$it" }

    @Test fun crashAndAnrRemainDifferentFromManualStopAndPackageUpdate() {
        val text=summary(ProcessExitRecord(40,4,4,0),ProcessExitRecord(30,3,6,0),ProcessExitRecord(20,2,10,0),ProcessExitRecord(10,1,16,0))
        assertTrue(text.contains("Java 崩溃"));assertTrue(text.contains("无响应（ANR）"))
        assertTrue(text.contains("原因码 4"));assertTrue(text.contains("原因码 16"))
        assertTrue(text.contains("用户请求终止"));assertTrue(text.contains("安装更新"))
        assertTrue(text.contains("不等于无障碍服务崩溃"))
        assertTrue(text.contains("版本未知"))
    }

    @Test fun emptyRecordsDoNotClaimThatNoFailureOccurred() {
        val text=summary()
        assertTrue(text.contains("没有可用的进程退出记录"))
        assertTrue(text.contains("不能据此判断没有发生故障"))
        assertFalse(text.contains("正常运行"))
    }

    @Test fun reportsOnlyLatestFiveValidRecordsInTimeOrder() {
        val rows=(1..8).reversed().map { ProcessExitRecord(it.toLong(),it,3,0) }+ProcessExitRecord(-1,9,4,0)+ProcessExitRecord(100,0,4,0)
        val text=ProcessExitSummary.describe(rows) { "time-$it" }
        assertTrue(text.indexOf("time-8")<text.indexOf("time-7"))
        assertTrue(text.contains("time-4"));assertFalse(text.contains("time-3"))
        assertFalse(text.contains("PID 9"));assertFalse(text.contains("PID 0"))
        assertEquals(5,Regex("PID ").findAll(text).count())
    }

    @Test fun unknownReasonRetainsItsNumberWithoutInventingACrash() {
        val text=summary(ProcessExitRecord(1,7,99,12))
        assertTrue(text.contains("未知原因（99）"));assertTrue(text.contains("状态码 12"))
        assertFalse(text.contains("Java 崩溃"))
    }

    @Test fun systemKillReasonsAreReportedWithoutAttributingThemToColorOs() {
        val text=summary(ProcessExitRecord(3,3,3,0),ProcessExitRecord(2,2,15,0),ProcessExitRecord(1,1,9,0))
        assertTrue(text.contains("内存不足"));assertTrue(text.contains("软件包状态变化"));assertTrue(text.contains("资源使用限制"))
        assertFalse(text.contains("ColorOS"))
    }
}
