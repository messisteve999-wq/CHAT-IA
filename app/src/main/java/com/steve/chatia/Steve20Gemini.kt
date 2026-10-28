package com.steve.chatia

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Steve20Gemini {
    fun reply(apiKey: String, sender: String, message: String): String {
        require(apiKey.isNotBlank()) { "Ajoute ta clé API Gemini dans Steve-20." }
        val prompt = """
            Tu es Steve-20, assistant de rédaction pour WhatsApp.
            Propose UNE réponse naturelle, courte et pertinente en français.
            Expéditeur : ${sender.take(120)}
            Message reçu : ${message.take(1800)}
            N'invente pas de faits, reste respectueux et ne prétends pas être l'utilisateur.
            Retourne uniquement le texte de la réponse, sans guillemets ni explication.
        """.trimIndent()
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            val body = JSONObject()
                .put("contents", org.json.JSONArray().put(JSONObject().put(
                    "parts", org.json.JSONArray().put(JSONObject().put("text", prompt))
                )))
                .put("generationConfig", JSONObject().put("temperature", 0.65).put("maxOutputTokens", 220))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val messageError = try { JSONObject(raw).getJSONObject("error").optString("message", raw) }
                    catch (_: Exception) { raw }
                throw IllegalStateException("Gemini ($status) : $messageError")
            }
            val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Réponse Gemini vide.")
            return (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }
                .joinToString("\n").trim().ifBlank { throw IllegalStateException("Réponse Gemini vide.") }
        } finally {
            connection.disconnect()
        }
    }
}
