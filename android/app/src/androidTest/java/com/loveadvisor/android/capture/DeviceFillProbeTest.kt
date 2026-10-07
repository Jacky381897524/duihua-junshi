package com.loveadvisor.android.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit USB probe. No model requests and no text writes. */
class DeviceFillProbeTest {
    @Test fun inspectNativeEditorAndOfficialInputConnection() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val until=System.currentTimeMillis()+12000
        while(ChatCaptureService.instance==null && System.currentTimeMillis()<until) Thread.sleep(100)
        val s=ChatCaptureService.instance ?: error("Service not connected")
        i.runOnMainSync { s.serviceInfo=s.serviceInfo.apply { flags=flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR } }
        Thread.sleep(800)
        val r=s.readFillWindow()
        val im=if(Build.VERSION.SDK_INT>=33)s.inputMethod else null
        val editor=im?.currentInputEditorInfo
        val connection=im?.currentInputConnection
        val surrounding=connection?.getSurroundingText(4096,4096,0)
        val report="nodes=${r?.window?.nodes?.size}; editable=${r?.editables?.size}; package=${r?.window?.packageName}; inputPackage=${editor?.packageName}; inputType=${editor?.inputType}; fieldId=${editor?.fieldId}; connection=${connection!=null}; surrounding=${surrounding!=null}; draftLength=${surrounding?.text?.length}"
        File(i.targetContext.filesDir,"fill-probe.txt").writeText(report)
        assertEquals("com.tencent.mm",editor?.packageName)
        assertNotNull("Official input connection unavailable",connection)
        assertNotNull("Input connection could not read current draft",surrounding)
    }
}
