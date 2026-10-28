package com.steve.chatia

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var apiKey: EditText
    private lateinit var autoReply: CheckBox
    private lateinit var latest: TextView
    private lateinit var suggestion: EditText
    private lateinit var status: TextView

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chatia", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setBackgroundColor(0xFFF7F3FF.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(TextView(this).apply {
            text = "Steve-20"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(0xFF673AB7.toInt())
            setPadding(0, dp(10), 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = "Assistant flottant pour WhatsApp et WhatsApp Business"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        apiKey = EditText(this).apply {
            hint = "Clé API Gemini"
            setSingleLine(true)
            setText(prefs.getString("api_key", ""))
        }
        content.addView(apiKey)
        content.addView(Button(this).apply {
            text = "Enregistrer la clé Gemini"
            setOnClickListener {
                val key = apiKey.text.toString().trim()
                if (key.isBlank()) toast("Entre ta clé API Gemini.") else {
                    prefs.edit().putString("api_key", key).apply()
                    toast("Clé enregistrée sur ce téléphone.")
                }
            }
        })
        autoReply = CheckBox(this).apply {
            text = "Activer les réponses automatiques"
            isChecked = prefs.getBoolean("automatic_reply", false)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("automatic_reply", checked).apply()
                toast(if (checked) "Mode automatique activé. Teste-le d'abord avec un contact de confiance." else "Mode validation manuelle activé.")
            }
        }
        content.addView(autoReply)
        content.addView(Button(this).apply {
            text = "Autoriser les notifications WhatsApp"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })
        content.addView(Button(this).apply {
            text = "Autoriser la fenêtre flottante"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) toast("Autorisation déjà accordée.")
                else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        content.addView(Button(this).apply {
            text = "Afficher / masquer la bulle Steve-20"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else startService(Intent(this@MainActivity, FloatingChatService::class.java))
            }
        })
        content.addView(TextView(this).apply {
            text = "Dernier message détecté"
            textSize = 18f
            setTextColor(0xFF673AB7.toInt())
            setPadding(0, dp(16), 0, dp(4))
        })
        latest = TextView(this).apply { textSize = 15f; setTextColor(0xFF222222.toInt()); setPadding(0, dp(4), 0, dp(8)) }
        content.addView(latest)
        content.addView(Button(this).apply {
            text = "Actualiser le dernier message"
            setOnClickListener { refreshLatest() }
        })
        content.addView(Button(this).apply {
            text = "Générer une suggestion avec Gemini"
            setOnClickListener { generateSuggestion() }
        })
        suggestion = EditText(this).apply {
            hint = "La suggestion apparaîtra ici. Tu peux la modifier."
            minLines = 3
            gravity = Gravity.TOP
        }
        content.addView(suggestion)
        content.addView(Button(this).apply {
            text = "Envoyer depuis la notification WhatsApp"
            setOnClickListener {
                val text = suggestion.text.toString().trim()
                if (text.isBlank()) toast("Génère ou écris une réponse d'abord.")
                else if (NotificationReplyListener.sendLatestReply(text)) toast("Réponse envoyée.")
                else toast("Envoi direct indisponible pour cette notification. Ouvre WhatsApp et réponds manuellement.")
            }
        })
        content.addView(Button(this).apply {
            text = "Copier la réponse"
            setOnClickListener {
                val text = suggestion.text.toString().trim()
                if (text.isBlank()) toast("Aucune réponse à copier.") else {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Steve-20", text))
                    toast("Réponse copiée.")
                }
            }
        })
        status = TextView(this).apply { textSize = 13f; setPadding(0, dp(12), 0, dp(8)) }
        content.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshLatest()
    }

    private fun refreshLatest() {
        val prefs = getSharedPreferences("chatia", MODE_PRIVATE)
        val sender = prefs.getString("last_sender", "") ?: ""
        val message = prefs.getString("last_message", "") ?: ""
        val app = when (prefs.getString("last_package", "")) {
            "com.whatsapp" -> "WhatsApp"
            "com.whatsapp.w4b" -> "WhatsApp Business"
            else -> "Application inconnue"
        }
        latest.text = if (message.isBlank()) "Aucun message détecté. Autorise l'accès aux notifications puis attends un nouveau message." else "$app · $sender\n$message"
        status.text = "Accès notifications : ${if (NotificationReplyListener.notificationAccessEnabled(this)) "autorisé" else "à autoriser"}\nBulle : ${if (Settings.canDrawOverlays(this)) "autorisée" else "à autoriser"}"
    }

    private fun generateSuggestion() {
        val prefs = getSharedPreferences("chatia", MODE_PRIVATE)
        val key = apiKey.text.toString().trim().ifBlank { prefs.getString("api_key", "").orEmpty() }
        val sender = prefs.getString("last_sender", "").orEmpty()
        val message = prefs.getString("last_message", "").orEmpty()
        if (key.isBlank()) { toast("Ajoute et enregistre ta clé Gemini."); return }
        if (message.isBlank()) { toast("Aucun message détecté. Autorise les notifications et reçois un message."); return }
        prefs.edit().putString("api_key", key).apply()
        suggestion.setText("Génération en cours…")
        executor.execute {
            val result = try { Steve20Gemini.reply(key, sender, message) } catch (e: Exception) { "Erreur : ${e.message ?: "Gemini indisponible"}" }
            runOnUiThread { suggestion.setText(result) }
        }
    }

    override fun onResume() { super.onResume(); if (::latest.isInitialized) refreshLatest() }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
