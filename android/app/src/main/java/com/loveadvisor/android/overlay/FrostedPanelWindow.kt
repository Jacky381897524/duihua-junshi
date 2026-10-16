package com.loveadvisor.android.overlay

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.loveadvisor.android.R
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.FrostedPanelPolicy
import java.util.function.Consumer

/** Native compositor blur is clipped to this small window; no screenshots or frame polling. */
@RequiresApi(31)
internal class FrostedPanelWindow(private val context: Context, private val content: AdvisorPanelView) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val palette = PanelPalette(context, frosted = true)
    private val backdrop = ViewStyles.shape(context, palette.opaqueSurface, 24f)
    private val dialog = Dialog(context, R.style.Theme_LoveAdvisor_FrostedPanel)
    private var visible = false
    private var blurListenerRegistered = false
    private var powerReceiverRegistered = false
    private var lastRadius = -1
    private var blurFailed = false
    private val blurListener = Consumer<Boolean> { if (visible) refreshBlur() }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (visible) refreshBlur() }
    }

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setContentView(content, ViewGroup.LayoutParams(-1, -1))
        dialog.window!!.apply {
            setBackgroundDrawable(backdrop)
            decorView.setPadding(0, 0, 0, 0)
            decorView.clipToOutline = true
        }
    }

    fun show(params: WindowManager.LayoutParams) {
        update(params)
        dialog.show()
        visible = true
        update(params)
        if (!blurListenerRegistered) {
            blurListenerRegistered = true
            manager.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
        }
        if (!powerReceiverRegistered) {
            powerReceiverRegistered = true
            ContextCompat.registerReceiver(context, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        refreshBlur()
    }

    fun update(params: WindowManager.LayoutParams) {
        val window = dialog.window!!
        val next = WindowManager.LayoutParams().apply {
            copyFrom(params)
            token = window.attributes.token ?: params.token
            flags = flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            // Never blur/dim the rest of the chat, steal editor focus, or resize it.
            flags = flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv() and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            windowAnimations = 0
            setFitInsetsTypes(0)
        }
        window.attributes = next
    }

    private fun refreshBlur() {
        val supported = !blurFailed && runCatching { manager.isCrossWindowBlurEnabled }.getOrDefault(false)
        val saving = runCatching { power?.isPowerSaveMode == true }.getOrDefault(true)
        val radius = FrostedPanelPolicy.radiusPx(Build.VERSION.SDK_INT, true, supported, saving, context.resources.displayMetrics.density)
        if (lastRadius == radius) return
        lastRadius = radius
        backdrop.setColor(if (radius > 0) palette.surface else palette.opaqueSurface)
        try {
            dialog.window!!.setBackgroundBlurRadius(radius)
        } catch (_: RuntimeException) {
            blurFailed = true
            lastRadius = 0
            backdrop.setColor(palette.opaqueSurface)
            runCatching { dialog.window?.setBackgroundBlurRadius(0) }
        }
    }

    fun hide() {
        visible = false
        if (blurListenerRegistered) {
            runCatching { manager.removeCrossWindowBlurEnabledListener(blurListener) }
            blurListenerRegistered = false
        }
        if (powerReceiverRegistered) {
            runCatching { context.unregisterReceiver(powerReceiver) }
            powerReceiverRegistered = false
        }
        runCatching { dialog.window?.setBackgroundBlurRadius(0) }
        lastRadius = -1
        dialog.dismiss()
    }

    fun release() {
        hide()
        (content.parent as? ViewGroup)?.removeView(content)
    }
}
