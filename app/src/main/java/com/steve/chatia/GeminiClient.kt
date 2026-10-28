package com.steve.chatia

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    fun generate(
        apiKey: String,
        theme: String,
        count: Int
    ): String {
        require(apiKey.isNotBlank()) {
            "Entre d'abord ta clé API Gemini."
        }

        val prompt = """
            Tu es CHAT-IA, un assistant qui aide à faire connaissance
            et à discuter naturellement en français.
            Thème : $theme.
            Génère exactement $count propositions différentes,
            naturelles, courtes et faciles à envoyer dans une conversation.
            Adapte le ton au thème, sans manipulation ni pression.
            Numérote chaque proposition sur une ligne distincte.
            Ne donne aucune introduction ni conclusion.
        """.trimIndent()

        val connection = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        ).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)

            val body = JSONObject()
                .put(
                    "contents",
                    org.json.JSONArray().put(
                        JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(
                                JSONObject().put("text", prompt)
                            )
                        )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.85)
                        .put("maxOutputTokens", 1800)
                )

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream?.bufferedReader()?.use { it.readText() }
                ?: ""

            if (status !in 200..299) {
                val message = try {
                    JSONObject(response)
                        .getJSONObject("error")
                        .optString("message", response)
                } catch (_: Exception) {
                    response
                }
                return "Erreur Gemini ($status) : $message"
            }

            val json = JSONObject(response)
            val candidates = json.optJSONArray("candidates")
                ?: return "Gemini n'a retourné aucune réponse."

            val parts = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return "La réponse de Gemini est vide."

            val result = StringBuilder()
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text", "") ?: ""
                if (text.isNotBlank()) {
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(text)
                }
            }

            result.toString().ifBlank {
                "Aucune proposition reçue. Réessaie."
            }
        } catch (e: Exception) {
            "Connexion impossible : ${e.message ?: "erreur inconnue"}"
        } finally {
            connection.disconnect()
        }
    }
}
