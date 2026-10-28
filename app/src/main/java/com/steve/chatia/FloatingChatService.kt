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
import android.widget.Toast

class FloatingChatService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private val themes = arrayOf(
        "Faire connaissance", "Drague amicale", "Drague romantique",
        "Vie quotidienne", "Humour et taquinerie", "Discussion profonde",
        "Relancer une conversation"
    )

    private fun updateBubble() {
        val index = getSharedPreferences("chatia", MODE_PRIVATE)
            .getInt("theme_index", 0).coerceIn(themes.indices)
        bubble?.text = "STEVE-20\n${themes[index]}"
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF673AB7.toInt())
            setPadding(20, 14, 20, 14)
            gravity = Gravity.CENTER
            elevation = 12f
            setOnClickListener {
                val intent = Intent(this@FloatingChatService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            setOnLongClickListener {
                val prefs = getSharedPreferences("chatia", MODE_PRIVATE)
                val current = prefs.getInt("theme_index", 0).coerceIn(themes.indices)
                val next = (current + 1) % themes.size
                prefs.edit().putInt("theme_index", next).apply()
                updateBubble()
                Toast.makeText(this@FloatingChatService, "Thème : ${themes[next]}", Toast.LENGTH_SHORT).show()
                true
            }
        }
        updateBubble()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 180 }
        windowManager?.addView(bubble, params)
    }

    override fun onDestroy() {
        bubble?.let { if (it.isAttachedToWindow) windowManager?.removeView(it) }
        bubble = null
        windowManager = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
