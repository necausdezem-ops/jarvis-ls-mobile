package com.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val Obsidian = Color(0xFF05090F)
private val Panel = Color(0xFF0A1620)
private val Cyan = Color(0xFF00D9FF)
private val Amber = Color(0xFFFF9B45)
private val Muted = Color(0xFF8DA5B2)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 9, 15)
        window.navigationBarColor = android.graphics.Color.rgb(5, 9, 15)
        tts = TextToSpeech(this) { state ->
            if (state == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("pt", "BR")
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Amber, background = Obsidian, surface = Panel)) {
                JarvisHome(
                    speak = { text -> if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis") },
                    stopSpeaking = { tts?.stop() }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

private data class Message(val jarvis: Boolean, val text: String)

@Composable
private fun JarvisHome(speak: (String) -> Unit, stopSpeaking: () -> Unit) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf(Message(true, "J.A.R.V.I.S. LS Mobile iniciado. Sistemas locais disponíveis.")) }
    var input by remember { mutableStateOf("") }
    var localModelReady by remember { mutableStateOf(LocalAiEngine.isModelInstalled(context)) }
    var downloadProgress by remember { mutableStateOf(-1f) }
    var showSettings by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("SISTEMA PRONTO · IA LOCAL") }
    val scope = rememberCoroutineScope()

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        messages.add(Message(false, text))
        input = ""
        thinking = true
        status = "ANALISANDO COMANDO"
        scope.launch {
            val local = localCommand(context, text)
            val answer = if (local != null) {
                local
            } else if (LocalAiEngine.isModelInstalled(context)) {
                status = "IA LOCAL PROCESSANDO"
                try {
                    LocalAiEngine.reply(context, text)
                } catch (e: Exception) {
                    "Não consegui iniciar a IA local: ${e.message ?: "erro desconhecido"}. Tente reiniciar o aplicativo."
                }
            } else {
                "Meu modelo de IA ainda não foi instalado. Abra Configurações e toque em BAIXAR IA LOCAL. O download inicial tem cerca de 347 MB; depois poderei conversar offline."
            }
            messages.add(Message(true, answer))
            thinking = false
            localModelReady = LocalAiEngine.isModelInstalled(context)
            status = if (local != null) "COMANDO LOCAL CONCLUÍDO" else if (localModelReady) "RESPOSTA GERADA OFFLINE" else "AGUARDANDO INSTALAÇÃO DA IA"
            speak(answer)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        listening = false
        if (!spoken.isNullOrBlank()) sendMessage(spoken) else status = "NENHUMA FALA RECONHECIDA"
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                listening = true
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o J.A.R.V.I.S.")
                }
                voiceLauncher.launch(intent)
            } else status = "SERVIÇO DE VOZ INDISPONÍVEL"
        } else status = "PERMISSÃO DE MICROFONE NEGADA"
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071722), Obsidian, Color(0xFF03070B))))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("J.A.R.V.I.S.", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("LS MOBILE / ASSISTANT CORE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Configurações", tint = Cyan) }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(178.dp), contentAlignment = Alignment.Center) {
            CoreHud(active = listening || thinking)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (listening) "OUVINDO" else if (thinking) "PROCESSANDO" else "ONLINE", color = if (thinking || listening) Amber else Cyan, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text("CORE", color = Muted, fontSize = 10.sp, letterSpacing = 4.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoTile("BATERIA", batteryPercent(context).toString() + "%", Modifier.weight(1f))
            InfoTile("HORA", SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()), Modifier.weight(1f))
            InfoTile("IA", if (localModelReady) "PRONTA" else "BAIXAR", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(status, color = Cyan.copy(alpha = 0.8f), fontSize = 10.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.jarvis) Alignment.Start else Alignment.End) {
                    Text(if (message.jarvis) "J.A.R.V.I.S." else "VOCÊ", color = if (message.jarvis) Cyan else Amber, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Surface(color = if (message.jarvis) Panel else Color(0xFF14212A), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, (if (message.jarvis) Cyan else Amber).copy(alpha = 0.25f))) {
                        Text(message.text, color = Color(0xFFE2F6FC), fontSize = 13.sp, modifier = Modifier.padding(11.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).padding(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan),
                decorationBox = { inner -> Box { if (input.isEmpty()) Text("Fale um comando...", color = Muted, fontSize = 14.sp); inner() } }
            )
            IconButton(onClick = { stopSpeaking(); status = "FALA INTERROMPIDA" }) { Icon(Icons.Default.Stop, "Parar fala", tint = Amber) }
            IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Icon(Icons.Default.Mic, "Falar", tint = Cyan) }
            IconButton(onClick = { sendMessage(input) }, enabled = input.isNotBlank() && !thinking) { Icon(Icons.Default.Send, "Enviar", tint = Cyan) }
        }
        Text("TOQUE NO MICROFONE PARA FALAR · AÇÕES SENSÍVEIS EXIGEM CONTROLE MANUAL", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("Configurações do Core", color = Cyan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CÉREBRO OFFLINE · QWEN3 0.6B INT4", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("O modelo roda dentro deste aplicativo. O download inicial tem aproximadamente 347 MB e precisa de internet; depois as respostas de texto funcionam offline.", color = Muted, fontSize = 12.sp)
                    if (localModelReady) {
                        Text("MODELO INSTALADO · PRONTO PARA USAR OFFLINE", color = Cyan, fontSize = 11.sp)
                    } else if (downloadProgress >= 0f) {
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(), color = Cyan)
                        Text("BAIXANDO MODELO: ${(downloadProgress * 100).toInt()}%", color = Amber, fontSize = 11.sp)
                    } else {
                        Text("Recomendado: Wi-Fi e pelo menos 1 GB de espaço livre.", color = Amber, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !localModelReady && downloadProgress < 0f,
                    onClick = {
                        scope.launch {
                            try {
                                downloadProgress = 0f
                                status = "BAIXANDO CÉREBRO LOCAL"
                                LocalAiEngine.downloadModel(context) { p -> downloadProgress = p }
                                localModelReady = true
                                downloadProgress = -1f
                                status = "IA LOCAL INSTALADA"
                            } catch (e: Exception) {
                                downloadProgress = -1f
                                status = "FALHA NO DOWNLOAD DA IA"
                                messages.add(Message(true, "Não consegui baixar o modelo. Confere a internet e tenta de novo. Detalhe: ${e.message ?: "erro desconhecido"}"))
                            }
                        }
                    }
                ) { Text(if (localModelReady) "IA INSTALADA" else if (downloadProgress >= 0f) "BAIXANDO..." else "BAIXAR IA LOCAL", color = Cyan) }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("FECHAR", color = Amber) } }
        )
    }
}

@Composable
private fun CoreHud(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "hud")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 3500 else 12000, easing = LinearEasing), RepeatMode.Restart), label = "rotation")
    val pulse by transition.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.size(170.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.43f
        drawCircle(Cyan.copy(alpha = 0.08f), r * pulse)
        drawCircle(Cyan.copy(alpha = 0.8f), r, style = Stroke(width = 1.3.dp.toPx()))
        drawCircle(Cyan.copy(alpha = 0.35f), r * 0.78f, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Amber.copy(alpha = 0.7f), r * 0.55f, style = Stroke(width = 2.dp.toPx()))
        for (i in 0 until 48) {
            val angle = Math.toRadians((i * 7.5 + rotation).toDouble())
            val inner = r * if (i % 4 == 0) 0.82f else 0.9f
            val outer = r * if (i % 4 == 0) 1f else 0.95f
            drawLine(Cyan.copy(alpha = if (i % 4 == 0) 0.9f else 0.35f),
                Offset(c.x + cos(angle).toFloat() * inner, c.y + sin(angle).toFloat() * inner),
                Offset(c.x + cos(angle).toFloat() * outer, c.y + sin(angle).toFloat() * outer),
                strokeWidth = if (i % 4 == 0) 2.dp.toPx() else 1.dp.toPx())
        }
        drawCircle(Cyan.copy(alpha = 0.8f), r * 0.23f)
        drawCircle(Obsidian, r * 0.17f)
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Panel).border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    return if (level in 0..100) level else 0
}

private suspend fun localCommand(context: Context, text: String): String? {
    val q = text.lowercase(Locale("pt", "BR"))
    return when {
        "que horas" in q || q == "hora" -> "Agora são " + SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()) + "."
        "bateria" in q -> "A bateria está em " + batteryPercent(context) + "%."
        "configurações" in q || "configuracoes" in q -> {
            withContext(Dispatchers.Main) { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "Abrindo as configurações do Android."
        }
        "wifi" in q || "wi-fi" in q -> {
            withContext(Dispatchers.Main) { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "Abrindo as configurações de Wi-Fi."
        }
        "bluetooth" in q -> {
            withContext(Dispatchers.Main) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "Abrindo as configurações de Bluetooth."
        }
        "abrir youtube" in q -> openAppOrWeb(context, "com.google.android.youtube", "https://youtube.com")
        "abrir tiktok" in q -> openAppOrWeb(context, "com.zhiliaoapp.musically", "https://tiktok.com")
        else -> null
    }
}

private fun openAppOrWeb(context: Context, packageName: String, url: String): String {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    val intent = launch ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    return if (launch != null) "Abrindo o aplicativo solicitado." else "O aplicativo não foi encontrado; abrindo a versão web."
}
