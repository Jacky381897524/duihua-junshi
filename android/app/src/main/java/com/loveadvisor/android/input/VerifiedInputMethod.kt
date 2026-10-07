package com.loveadvisor.android.input
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi

/** A restart/finish invalidates any pending transaction even within the same WeChat window. */
@RequiresApi(33)
class VerifiedInputMethod(service:AccessibilityService,private val changed:()->Unit):InputMethod(service) {
    override fun onStartInput(attribute:EditorInfo,restarting:Boolean) { changed();super.onStartInput(attribute,restarting) }
    override fun onFinishInput() { changed();super.onFinishInput() }
}
