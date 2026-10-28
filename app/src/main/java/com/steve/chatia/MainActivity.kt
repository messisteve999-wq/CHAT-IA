package com.steve.chatia

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private val themes = arrayOf(
        "Faire connaissance",
        "Drague amicale",
        "Drague romantique",
        "Vie quotidienne",
        "Humour et taquinerie",
        "Discussion profonde",
        "Relancer une conversation"
    )

    private lateinit var apiKey: EditText
    private lateinit var themeSpinner: Spinner
    private lateinit var countSpinner: Spinner
    private lateinit var results: TextView
    private lateinit var generateButton: Button

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences("chatia", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFF7F3FF.toInt())
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "CHAT-IA"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(0xFF673AB7.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })

        content.addView(TextView(this).apply {
            text = "Des idées naturelles pour discuter, faire connaissance et relancer une conversation."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(16))
        })

        apiKey = EditText(this).apply {
            hint = "Clé API Gemini"
            setSingleLine(true)
            setText(preferences.getString("api_key", ""))
        }
        content.addView(apiKey)

        content.addView(TextView(this).apply {
            text = "Choisis un thème"
            textSize = 16f
            setPadding(0, dp(16), 0, dp(4))
        })

        themeSpinner = Spinner(this)
        themeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            themes
        )
        themeSpinner.setSelection(
            preferences.getInt("theme_index", 0).coerceIn(themes.indices)
        )
        themeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    preferences.edit()
                        .putInt("theme_index", position)
                        .apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        content.addView(themeSpinner)

        content.addView(TextView(this).apply {
            text = "Nombre de propositions"
            textSize = 16f
            setPadding(0, dp(16), 0, dp(4))
        })

        countSpinner = Spinner(this)
        countSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("10", "15", "20")
        )
        content.addView(countSpinner)

        generateButton = Button(this).apply {
            text = "Générer avec Gemini"
            setOnClickListener { generate() }
        }
        content.addView(generateButton)

        content.addView(Button(this).apply {
            text = "Ouvrir la bulle flottante"
            setOnClickListener {
                if (android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                    startService(
                        android.content.Intent(
                            this@MainActivity,
                            FloatingChatService::class.java
                        )
                    )
                } else {
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        })


        content.addView(Button(this).apply {
            text = "Copier les propositions"
            setOnClickListener {
                val text = results.text.toString()
                if (text.isBlank() ||
                    text == "Tes propositions apparaîtront ici." ||
                    text == "Génération en cours…"
                ) {
                    toast("Génère d'abord des propositions.")
                } else {
                    val clipboard = getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("CHAT-IA", text)
                    )
                    toast("Propositions copiées.")
                }
            }
        })

        results = TextView(this).apply {
            text = "Tes propositions apparaîtront ici."
            textSize = 16f
            setTextColor(0xFF222222.toInt())
            setPadding(0, dp(18), 0, dp(18))
            setTextIsSelectable(true)
        }
        content.addView(results)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun generate() {
        val key = apiKey.text.toString().trim()

        if (key.isBlank()) {
            toast("Entre ta clé API Gemini.")
            return
        }

        getSharedPreferences("chatia", MODE_PRIVATE)
            .edit()
            .putString("api_key", key)
            .apply()

        val theme = themes[themeSpinner.selectedItemPosition]
        val count = countSpinner.selectedItem.toString().toInt()

        generateButton.isEnabled = false
        results.text = "Génération en cours…"

        executor.execute {
            val response = try {
                GeminiClient.generate(key, theme, count)
            } catch (e: Exception) {
                "Erreur : ${e.message ?: "impossible de contacter Gemini."}"
            }

            runOnUiThread {
                results.text = response
                generateButton.isEnabled = true
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
