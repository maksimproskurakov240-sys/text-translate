package com.example.remotetts

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import okhttp3.*
import java.util.Locale
import java.util.concurrent.TimeUnit

class TtsService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    // ВСТАВЛЕН ТВОЙ NGROK АДРЕС (обязательно wss:// для https)
    private val serverUrl = "wss://roundup-dangling-reoccupy.ngrok-free.dev"

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        tts = TextToSpeech(this, this)
        connectWebSocket()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                val parts = text.split("|", limit = 3)
                if (parts.size >= 2 && parts[0] == "SPEAK") {
                    val speakText = parts[1]
                    if (parts.size == 3 && parts[2].isNotEmpty()) {
                        val level = parts[2].toIntOrNull() ?: 50
                        val am = getSystemService(AUDIO_SERVICE) as AudioManager
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, level * max / 100, 0)
                    }
                    if (speakText.isNotEmpty()) {
                        tts?.setPitch(0.6f)
                        tts?.setSpeechRate(0.9f)
                        tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "remote")
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                android.os.Handler(mainLooper).postDelayed({ connectWebSocket() }, 5000)
            }
        })
    }

    private fun buildNotification(): Notification {
        val channelId = "tts_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Remote TTS", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remote TTS")
            .setContentText("Ожидание команд")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        webSocket?.close(1000, null)
        tts?.shutdown()
        super.onDestroy()
    }
}