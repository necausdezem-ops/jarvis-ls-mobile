package com.jarvis.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat completions client. API calls require internet and may be billed
 * by the selected provider. The API key is stored in private app preferences on-device.
 */
object ApiAiEngine {
    suspend fun reply(baseUrl: String, apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "A chave da API está vazia" }
            require(model.isNotBlank()) { "Informe o nome do modelo" }
            val normalizedBase = baseUrl.trim().trimEnd('/')
            require(normalizedBase.startsWith("https://")) { "Use uma URL HTTPS segura para a API" }
            val endpoint = if (normalizedBase.endsWith("/chat/completions")) normalizedBase else "$normalizedBase/chat/completions"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 90000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
            try {
                val messages = JSONArray()
                    .put(JSONObject()
                        .put("role", "system")
                        .put("content", "Você é J.A.R.V.I.S., assistente pessoal útil, inteligente e direto. Responda em português brasileiro, salvo pedido contrário. Nunca diga que executou ações no celular se elas não foram realmente executadas. Diferencie fatos de suposições."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
                val body = JSONObject()
                    .put("model", model.trim())
                    .put("messages", messages)
                    .put("temperature", 0.7)
                    .put("stream", false)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val detail = try {
                        JSONObject(responseText).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    throw IllegalStateException("HTTP $code" + if (!detail.isNullOrBlank()) ": $detail" else "")
                }
                val json = JSONObject(responseText)
                val answer = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                if (answer.isNullOrBlank() || answer == "null") {
                    throw IllegalStateException("O provedor retornou uma resposta vazia ou em formato incompatível")
                }
                answer
            } finally {
                connection.disconnect()
            }
        }
}
