package com.jarvis.mobile

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device text model manager. The model is downloaded once to app-private storage,
 * then all inference happens locally with LiteRT-LM.
 */
object LocalAiEngine {
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    private const val MODEL_FILE = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    private const val MIN_MODEL_BYTES = 250L * 1024L * 1024L

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    private fun modelFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, MODEL_FILE)

    fun isModelInstalled(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() >= MIN_MODEL_BYTES
    }

    suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            if (target.isFile && target.length() >= MIN_MODEL_BYTES) {
                onProgress(1f)
                return@withContext
            }
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "$MODEL_FILE.part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "JARVIS-LS-Mobile/0.2")
                }
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Servidor retornou HTTP $code")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (total > 0L) onProgress((downloaded.toFloat() / total).coerceIn(0f, 0.99f))
                        }
                    }
                }
                if (temp.length() < MIN_MODEL_BYTES) {
                    throw IllegalStateException("Arquivo baixado incompleto (${temp.length()} bytes)")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                onProgress(1f)
            } catch (e: Exception) {
                temp.delete()
                throw e
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun reply(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        check(isModelInstalled(context)) { "Modelo local não instalado" }
        var activeEngine = engine
        var activeConversation = conversation
        if (activeEngine == null || activeConversation == null) {
            synchronized(this@LocalAiEngine) {
                activeEngine = engine
                activeConversation = conversation
                if (activeEngine == null || activeConversation == null) {
                    val config = EngineConfig(
                        modelPath = modelFile(context).absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                    val created = Engine(config)
                    created.initialize()
                    val chat = created.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "Você é J.A.R.V.I.S., um assistente pessoal inteligente, direto e educado. " +
                                    "Responda sempre em português brasileiro, a menos que o usuário peça outro idioma. " +
                                    "Seja útil e honesto; não invente acesso ao celular ou à internet. " +
                                    "Você pode explicar comandos, mas só ações implementadas no aplicativo realmente serão executadas."
                            )
                        )
                    )
                    engine = created
                    conversation = chat
                    activeEngine = created
                    activeConversation = chat
                }
            }
        }
        val answer = activeConversation!!.sendMessage(prompt).toString().trim()
        answer.ifBlank { "Não consegui formular uma resposta. Tenta perguntar de outro jeito." }
    }

    fun close() {
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
        conversation = null
        engine = null
    }
}
