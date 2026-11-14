package com.example.remotetts

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBatteryOptimizationExemption()
        startTtsService()

        setContent {
            MaterialTheme {
                val screenWidth = LocalConfiguration.current.screenWidthDp
                val screenHeight = LocalConfiguration.current.screenHeightDp
                val buttonSize = (minOf(screenWidth, screenHeight) * 0.12f).dp

                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.your_image),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-16).dp, y = 16.dp)
                            .size(buttonSize)
                            .clickable {
                                playTickSound()
                                val serviceIntent = Intent(this@MainActivity, TtsService::class.java)
                                stopService(serviceIntent)
                                android.os.Handler(mainLooper).postDelayed({
                                    startTtsService()
                                }, 500)
                            }
                    )
                }
            }
        }
    }

    private fun playTickSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.tick)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
        } catch (_: Exception) {}
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun startTtsService() {
        val intent = Intent(this, TtsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}