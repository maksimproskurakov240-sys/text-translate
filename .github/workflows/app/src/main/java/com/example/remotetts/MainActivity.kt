package com.example.remotetts

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// Модель для сохранения прогресса
data class GameProgress(
    val bestScore: Int = 0,
    val unlockedSkins: List<String> = listOf("default"),
    val currentSkin: String = "default",
    val secretCodeEntered: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestBatteryOptimizationExemption()
        startTtsService()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F19)) {
                    GameScreen()
                }
            }
        }
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
}

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    val gson = com.google.gson.Gson()

    var progress by remember {
        val json = prefs.getString("progress", null)
        if (json != null) gson.fromJson(json, GameProgress::class.java) else GameProgress()
    }

    fun saveProgress(newProgress: GameProgress) {
        progress = newProgress
        prefs.edit().putString("progress", gson.toJson(newProgress)).apply()
    }

    var isGameOver by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var totalScore by remember { mutableIntStateOf(0) }
    
    // Физика
    var birdX by remember { mutableFloatStateOf(0f) }
    var birdY by remember { mutableFloatStateOf(0f) }
    var birdVy by remember { mutableFloatStateOf(0f) }
    var direction by remember { mutableIntStateOf(1) }
    var spikesLeft by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var spikesRight by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    
    var secretInput by remember { mutableStateOf("") }
    
    // Переменные для адаптации под экран
    var screenW by remember { mutableFloatStateOf(0f) }
    var screenH by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    val skinsList = listOf(
        "default" to 0, "mango" to 50, "bunny" to 100, "cat" to 150,
        "phoenix" to 200, "watermelon" to 250, "dragon" to 300, "cosmos" to 400
    )

    // Генерация шипов теперь зависит от высоты экрана
    fun generateSpikes() {
        if (screenH <= 0f) return
        fun gen(): List<Pair<Float, Float>> {
            val num = Random.nextInt(1, 4)
            return List(num) {
                val start = Random.nextInt(100, (screenH - 100).toInt()).toFloat()
                val height = Random.nextInt(60, 150).toFloat()
                start to (start + height)
            }
        }
        spikesLeft = gen()
        spikesRight = gen()
    }

    fun resetGame() {
        isGameOver = false
        isPaused = false
        totalScore = 0
        birdX = screenW / 2
        birdY = screenH / 2
        birdVy = 0f
        direction = 1
        generateSpikes()
    }

    // Игровой цикл
    LaunchedEffect(isGameOver, isPaused, isInitialized) {
        if (isGameOver || isPaused || !isInitialized) return@LaunchedEffect
        generateSpikes()

        while (!isGameOver && !isPaused) {
            delay(16L) 
            
            birdVy += 0.6f
            birdY += birdVy
            birdX += 8f * direction

            // Смерть об пол/потолок (с учетом ширины шипов)
            if (birdY < 40f || birdY > screenH - 40f) {
                isGameOver = true
                if (totalScore > progress.bestScore) saveProgress(progress.copy(bestScore = totalScore))
                break
            }

            // Отскок от стен
            var wallHit = false
            if (birdX >= screenW - 60f && direction == 1) {
                birdX = screenW - 60f; direction = -1; wallHit = true
            } else if (birdX <= 60f && direction == -1) {
                birdX = 60f; direction = 1; wallHit = true
            }

            // Проверка шипов на стенах
            if (wallHit) {
                val currentSpikes = if (direction == 1) spikesLeft else spikesRight
                var hitSpike = false
                for ((start, end) in currentSpikes) {
                    if (birdY in start..end) {
                        hitSpike = true; break
                    }
                }
                if (hitSpike) {
                    isGameOver = true
                    if (totalScore > progress.bestScore) saveProgress(progress.copy(bestScore = totalScore))
                    break
                } else {
                    totalScore++
                    generateSpikes()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F19))
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!isGameOver && !isPaused) {
                        birdVy = -8f 
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Инициализация размеров при первом рендере
            if (!isInitialized) {
                screenW = size.width
                screenH = size.height
                birdX = size.width / 2
                birdY = size.height / 2
                isInitialized = true
            }

            // Стены
            drawRect(Color(0xFF28283C), size = Size(40f, size.height))
            drawRect(Color(0xFF28283C), topLeft = Offset(size.width - 40f, 0f), size = Size(40f, size.height))

            // Шипы на стенах
            drawSpikes(spikesLeft, isLeft = true, screenHeight = size.height)
            drawSpikes(spikesRight, isLeft = false, screenHeight = size.height)

            // Верхние/нижние шипы (по всей ширине)
            for (x in 40..(size.width.toInt() - 60) step 20) {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(x.toFloat(), 0f); lineTo(x + 20f, 0f); lineTo(x + 10f, 15f); close()
                }, color = Color(0xFFFF2850))
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(x.toFloat(), size.height); lineTo(x + 20f, size.height); lineTo(x + 10f, size.height - 15f); close()
                }, color = Color(0xFFFF2850))
            }

            // Трейсер (шлейф)
            val trailColor = Color(0xFF64C8FF).copy(alpha = 0.3f)
            drawCircle(trailColor, radius = 10f, center = Offset(birdX - direction * 20f, birdY))
            drawCircle(trailColor.copy(alpha = 0.1f), radius = 15f, center = Offset(birdX - direction * 40f, birdY))

            // Отрисовка птицы
            drawSkin(progress.currentSkin, birdX, birdY, direction)
        }

        // UI: Очки
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            Text("SCORE: $totalScore", color = Color(0xFFFFDC32), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("BEST: ${maxOf(progress.bestScore, totalScore)}", color = Color(0xFF969696), fontSize = 16.sp)
        }

        // Кнопка Паузы
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(50.dp)
                .background(Color(0xFF323246), RoundedCornerShape(8.dp))
                .clickable { isPaused = !isPaused },
            contentAlignment = Alignment.Center
        ) {
            if (isPaused) {
                Row {
                    Box(Modifier.width(6.dp).height(24.dp).background(Color.White))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.width(6.dp).height(24.dp).background(Color.White))
                }
            } else {
                androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                    drawPath(androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f); lineTo(0f, size.height); lineTo(size.width, size.height / 2f); close()
                    }, color = Color.White)
                }
            }
        }

        // Меню паузы
        if (isPaused) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xEE1E1E28)).pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("PAUSED", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    BasicTextField(
                        value = secretInput,
                        onValueChange = { 
                            secretInput = it 
                            if (it.lowercase() == "кролл") {
                                saveProgress(progress.copy(secretCodeEntered = true))
                                secretInput = "SUCCESS"
                            }
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .background(Color(0xFF3C3C50), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Text("SKINS", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.align(Alignment.Start))
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(skinsList.size) { index ->
                            val (skinId, unlockScore) = skinsList[index]
                            val isUnlocked = progress.secretCodeEntered || progress.unlockedSkins.contains(skinId) || progress.bestScore >= unlockScore
                            
                            if (isUnlocked && !progress.unlockedSkins.contains(skinId)) {
                                saveProgress(progress.copy(unlockedSkins = progress.unlockedSkins + skinId))
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(
                                        if (progress.currentSkin == skinId) Color(0xFF64FF64) 
                                        else if (isUnlocked) Color(0xFF323246) 
                                        else Color(0xFF232333),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = isUnlocked) {
                                        saveProgress(progress.copy(currentSkin = skinId))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUnlocked) {
                                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                        drawSkin(skinId, size.width / 2, size.height / 2, 1, scale = 0.5f)
                                    }
                                } else {
                                    Text("$unlockScore", color = Color(0xFFFF5050), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Экран Game Over
        if (isGameOver && !isPaused) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GAME OVER", color = Color(0xFFFF3232), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Your Score: $totalScore", color = Color.White, fontSize = 24.sp)
                    Text("Best: ${maxOf(progress.bestScore, totalScore)}", color = Color(0xFFFFDC32), fontSize = 20.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { resetGame() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF32C832)),
                        modifier = Modifier.height(60.dp).width(200.dp)
                    ) {
                        Text("RESTART", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Вспомогательные функции отрисовки
fun DrawScope.drawSpikes(spikes: List<Pair<Float, Float>>, isLeft: Boolean, screenHeight: Float) {
    for ((start, end) in spikes) {
        var y = start
        while (y < end) {
            val path = androidx.compose.ui.graphics.Path()
            if (isLeft) {
                path.moveTo(40f, y); path.lineTo(40f, y + 15f); path.lineTo(60f, y + 7.5f)
            } else {
                path.moveTo(size.width - 40f, y); path.lineTo(size.width - 40f, y + 15f); path.lineTo(size.width - 60f, y + 7.5f)
            }
            path.close()
            drawPath(path, color = Color(0xFFFF2850))
            y += 15f
        }
    }
}

fun DrawScope.drawSkin(skin: String, x: Float, y: Float, direction: Int, scale: Float = 1f) {
    val w = 50f * scale
    val h = 40f * scale
    val left = x - w / 2
    val top = y - h / 2

    when (skin) {
        "bunny" -> {
            drawOval(Color(0xFFF0F0FA), Offset(left + 5f * scale, top + 10f * scale), Size(30f * scale, 25f * scale))
            drawOval(Color(0xFFF0F0FA), Offset(left + 20f * scale, top - 15f * scale), Size(10f * scale, 30f * scale))
            drawOval(Color(0xFFFFC8C8), Offset(left + 22f * scale, top - 10f * scale), Size(6f * scale, 20f * scale))
            drawCircle(Color(0xFFF0F0FA), radius = 12f * scale, center = Offset(left + 35f * scale, top + 15f * scale))
            drawCircle(Color(0xFFFF6464), radius = 4f * scale, center = Offset(left + if (direction == 1) 38f * scale else 32f * scale, top + 15f * scale))
            drawCircle(Color(0xFFFF9696), radius = 3f * scale, center = Offset(left + if (direction == 1) 45f * scale else 25f * scale, top + 18f * scale))
            drawCircle(Color.White, radius = 8f * scale, center = Offset(left + 5f * scale, top + 25f * scale))
        }
        "mango" -> {
            drawOval(Color(0xFFFFAA28), Offset(left + 5f * scale, top + 5f * scale), Size(30f * scale, 25f * scale))
            drawCircle(Color(0xFFFF641E), radius = 8f * scale, center = Offset(left + if (direction == 1) 30f * scale else 20f * scale, top + 12f * scale))
            drawCircle(Color(0xFF0F0F19), radius = 3f * scale, center = Offset(left + if (direction == 1) 32f * scale else 18f * scale, top + 12f * scale))
            drawPath(androidx.compose.ui.graphics.Path().apply {
                if (direction == 1) { moveTo(left + 38f * scale, top + 14f * scale); lineTo(left + 48f * scale, top + 18f * scale); lineTo(left + 38f * scale, top + 22f * scale) }
                else { moveTo(left + 12f * scale, top + 14f * scale); lineTo(left + 2f * scale, top + 18f * scale); lineTo(left + 12f * scale, top + 22f * scale) }
            }, color = Color(0xFF32C832))
        }
        "cat" -> {
            drawOval(Color(0xFF9696A0), Offset(left + 5f * scale, top + 10f * scale), Size(30f * scale, 25f * scale))
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(left + 20f * scale, top + 10f * scale); lineTo(left + 15f * scale, top - 5f * scale); lineTo(left + 30f * scale, top + 5f * scale)
            }, color = Color(0xFF9696A0))
            drawOval(Color(0xFF00FF00), Offset(left + 28f * scale, top + 12f * scale), Size(6f * scale, 8f * scale))
        }
        else -> {
            drawOval(Color(0xFFFFDC32), Offset(left + 5f * scale, top + 5f * scale), Size(30f * scale, 25f * scale))
            drawCircle(Color.White, radius = 8f * scale, center = Offset(left + if (direction == 1) 30f * scale else 20f * scale, top + 12f * scale))
            drawCircle(Color(0xFF0F0F19), radius = 3f * scale, center = Offset(left + if (direction == 1) 32f * scale else 18f * scale, top + 12f * scale))
            drawPath(androidx.compose.ui.graphics.Path().apply {
                if (direction == 1) { moveTo(left + 38f * scale, top + 18f * scale); lineTo(left + 48f * scale, top + 21f * scale); lineTo(left + 38f * scale, top + 24f * scale) }
                else { moveTo(left + 12f * scale, top + 18f * scale); lineTo(left + 2f * scale, top + 21f * scale); lineTo(left + 12f * scale, top + 24f * scale) }
            }, color = Color(0xFFFF3232))
        }
    }
}
