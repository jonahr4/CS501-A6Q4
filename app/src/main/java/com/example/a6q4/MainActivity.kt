package com.example.a6q4

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.a6q4.ui.theme.A6Q4Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            A6Q4Theme {
                GyroBallGame()
            }
        }
    }
}

// Main composable showing the simple gyroscope ball maze.
@Composable
fun GyroBallGame() {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Offset.Zero) }
    var ballPos by remember { mutableStateOf(Offset(220f, 220f)) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val ballRadius = 38f

    val obstacles = remember {
        listOf(
            Rect(Offset(80f, 260f), Size(360f, 32f)),
            Rect(Offset(120f, 520f), Size(32f, 260f)),
            Rect(Offset(260f, 500f), Size(360f, 36f)),
            Rect(Offset(520f, 260f), Size(36f, 420f)),
            Rect(Offset(180f, 860f), Size(420f, 32f))
        )
    }

    val sensor = remember {
        GyroTiltSensor(context) { tilt = it }
    }

    DisposableEffect(Unit) {
        sensor.start()
        onDispose { sensor.stop() }
    }

    LaunchedEffect(canvasSize) {
        var lastNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            val dt = if (lastNanos == 0L) 0.016f else ((frameNanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNanos = frameNanos

            if (canvasSize != IntSize.Zero) {
                val prevPos = ballPos
                var newVel = velocity + tilt * 180f * dt
                newVel *= 0.985f
                var newPos = ballPos + newVel

                val (fixedPos, fixedVel) = keepInBounds(
                    candidatePos = newPos,
                    previousPos = prevPos,
                    candidateVel = newVel,
                    radius = ballRadius,
                    bounds = canvasSize.toSize(),
                    obstacles = obstacles
                )

                ballPos = fixedPos
                velocity = fixedVel
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
        ) {
            drawRect(color = Color(0xFF0F1F3F), size = size)

            obstacles.forEach { rect ->
                drawRect(
                    color = Color(0xFF18CED8),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height)
                )
            }

            drawCircle(
                color = Color(0xFFFFC857),
                radius = ballRadius,
                center = ballPos
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tilt to roll the ball",
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFFFFE29A)),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Avoid the neon walls",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFAED9E0))
            )
        }
    }
}

private fun keepInBounds(
    candidatePos: Offset,
    previousPos: Offset,
    candidateVel: Offset,
    radius: Float,
    bounds: Size,
    obstacles: List<Rect>
): Pair<Offset, Offset> {
    var pos = candidatePos
    var vel = candidateVel

    if (pos.x - radius < 0f) {
        pos = Offset(radius, pos.y)
        vel = Offset(-vel.x * 0.45f, vel.y * 0.9f)
    }
    if (pos.y - radius < 0f) {
        pos = Offset(pos.x, radius)
        vel = Offset(vel.x * 0.9f, -vel.y * 0.45f)
    }
    if (pos.x + radius > bounds.width) {
        pos = Offset(bounds.width - radius, pos.y)
        vel = Offset(-vel.x * 0.45f, vel.y * 0.9f)
    }
    if (pos.y + radius > bounds.height) {
        pos = Offset(pos.x, bounds.height - radius)
        vel = Offset(vel.x * 0.9f, -vel.y * 0.45f)
    }

    obstacles.forEach { rect ->
        if (circleHitsRect(pos, radius, rect)) {
            pos = previousPos
            vel = Offset(-vel.x * 0.35f, -vel.y * 0.35f)
        }
    }

    return pos to vel
}

private fun circleHitsRect(center: Offset, radius: Float, rect: Rect): Boolean {
    val closestX = center.x.coerceIn(rect.left, rect.right)
    val closestY = center.y.coerceIn(rect.top, rect.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}

// Small helper listening to the gyroscope and mapping rotation into a tilt vector.
class GyroTiltSensor(
    context: Context,
    private val onTilt: (Offset) -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val rawX = -(event.values.getOrNull(1) ?: 0f)
        val rawY = event.values.getOrNull(0) ?: 0f
        onTilt(Offset(rawX, rawY))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
