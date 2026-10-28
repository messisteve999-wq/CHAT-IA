package com.steve.chatia

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class FloatingChatService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubble = TextView(this).apply {
            text = "CHAT-IA"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF673AB7.toInt())
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER
            elevation = 12f

            setOnClickListener {
                val intent = Intent(this@FloatingChatService, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 180
        }

        windowManager?.addView(bubble, params)
    }

    override fun onDestroy() {
        bubble?.let {
            windowManager?.removeView(it)
        }
        bubble = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
