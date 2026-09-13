package dev.rafaelbrauner.flowvoice.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import dev.rafaelbrauner.flowvoice.MainActivity

class FlowVoiceOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var button: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val overlay = Button(this).apply {
            text = "Ditar"
            setOnClickListener {
                val launch = Intent(this@FlowVoiceOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_START_DICTATION, true)
                }
                startActivity(launch)
            }
        }
        button = overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 48
            y = 180
        }
        manager.addView(overlay, params)
    }

    override fun onDestroy() {
        button?.let { windowManager?.removeView(it) }
        button = null
        windowManager = null
        running = false
        super.onDestroy()
    }

    companion object {
        var running: Boolean = false
            private set
    }
}
