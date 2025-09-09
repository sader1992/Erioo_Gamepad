package com.erioo.erioogamepad
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.erioo.erioogamepad.ui.theme.EriooGamepadTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket
import kotlin.math.roundToInt
import kotlin.math.sqrt

import androidx.compose.ui.platform.LocalDensity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        setContent {
            EriooGamepadTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    var ipAddress by remember { mutableStateOf(TextFieldValue("192.168.1.50")) }
    var connected by remember { mutableStateOf(false) }

    if (!connected) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Server IP") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    connected = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OK")
            }
        }
    } else {
        // Controller screen
        ControllerView(ipAddress.text)
    }
}

@Composable
fun GameButton(label: String, downMessage: String, upMessage: String, writer: PrintWriter?, scope: CoroutineScope) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch(Dispatchers.IO) {
                            try { writer?.println(downMessage) } catch (_: Exception) {}
                        }
                        tryAwaitRelease()
                        scope.launch(Dispatchers.IO) {
                            try { writer?.println(upMessage) } catch (_: Exception) {}
                        }
                    }
                )
            }
    ) {
        Text(
            label,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun Joystick(
    size: Dp = 250.dp,
    stickSize: Dp = 100.dp,
    sendIntervalMs: Long = 16L, // throttle: ~60 updates per second
    onMove: (xPercent: Float, yPercent: Float) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var lastSentTime by remember { mutableStateOf(0L) }

    val density = LocalDensity.current
    val stickRadiusPx = with(density) { stickSize.toPx() / 2 }
    val containerRadiusPx = with(density) { size.toPx() / 2 }


    var lastSentX by remember { mutableStateOf(0f) }
    var lastSentY by remember { mutableStateOf(0f) }
    val step = 0.1f
    val sendIntervalMs = 16L // ~60 updates per second


    Box(
        modifier = Modifier
            .size(size)
            .background(Color.Gray, shape = CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent() // waits for any touch
                        val touch = event.changes.firstOrNull() ?: continue

                        val dx = touch.position.x - size.toPx() / 2
                        val dy = touch.position.y - size.toPx() / 2
                        val distance = sqrt(dx * dx + dy * dy)
                        val maxDistance = size.toPx() / 2 - stickRadiusPx

                        offsetX = if (distance > maxDistance) dx * maxDistance / distance else dx
                        offsetY = if (distance > maxDistance) dy * maxDistance / distance else dy

                        onMove(offsetX / maxDistance, -offsetY / maxDistance)

                        if (touch.changedToUp()) {
                            offsetX = 0f
                            offsetY = 0f
                            onMove(0f, 0f)
                        }

                        touch.consume()
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (containerRadiusPx + offsetX - stickRadiusPx).roundToInt(),
                        y = (containerRadiusPx + offsetY - stickRadiusPx).roundToInt()
                    )
                }
                .size(stickSize)
                .background(Color.DarkGray, shape = CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val radius = size.toPx() / 2
                            val dx = offset.x - radius
                            val dy = offset.y - radius
                            offsetX = dx
                            offsetY = dy
                            onMove(offsetX / radius, -offsetY / radius)
                        },
                        onDragEnd = {
                            offsetX = 0f
                            offsetY = 0f
                            onMove(0f, 0f)
                        },
                        onDragCancel = {
                            offsetX = 0f
                            offsetY = 0f
                            onMove(0f, 0f)
                        },
                        onDrag = { _, dragAmount ->
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            val maxDistance = containerRadiusPx - stickRadiusPx
                            val distance = sqrt(offsetX * offsetX + offsetY * offsetY)
                            if (distance > maxDistance) {
                                val scale = maxDistance / distance
                                offsetX *= scale
                                offsetY *= scale
                            }
                            var normX = offsetX / maxDistance
                            var normY = -offsetY / maxDistance
                            normX = (normX / step).roundToInt() * step
                            normY = (normY / step).roundToInt() * step
                            val now = System.currentTimeMillis()
                            if ((now - lastSentTime >= sendIntervalMs) && (normX != lastSentX || normY != lastSentY) && (Math.abs(offsetX - lastSentX) > 0.05f || Math.abs(offsetY - lastSentY) > 0.05f)) {
                                onMove(normX, normY)
                                lastSentX = normX
                                lastSentY = normY
                                lastSentTime = now
                            }
                        }
                    )
                }
        )
    }
}

@Composable
fun ControllerView(ip: String) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Connecting...") }
    var writer by remember { mutableStateOf<PrintWriter?>(null) }
    LaunchedEffect(ip) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ip, 9000)
                writer = PrintWriter(socket.getOutputStream(), true)
                status = "Connected to: $ip"
            } catch (e: Exception) {
                status = "Failed to connect: ${e.message}"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp))
        Joystick(size = 250.dp) { x, y ->
            scope.launch(Dispatchers.IO) {
                writer?.println("JOY $x $y")
            }
        }

        //Box {
        //    Button(onClick = { }) { GameButton("↑", "↑", writer, scope) } // Top
        //    Button(onClick = { }, modifier = Modifier.offset(y = 64.dp)) { GameButton("↓", "↓", writer, scope) } // Bottom
        //    Button(onClick = { }, modifier = Modifier.offset(x = -64.dp, y = 32.dp)) { GameButton("←", "←", writer, scope) } // Left
        //    Button(onClick = { }, modifier = Modifier.offset(x = 64.dp, y = 32.dp)) { GameButton("→", "→", writer, scope) } // Right
        //}
        Spacer(Modifier.width(64.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { }) { GameButton("START", "START", "START_UP", writer, scope) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { }) { GameButton("BACK", "BACK", "BACK_UP", writer, scope) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        writer?.close()
                        writer = null
                        status = "Reconnecting..."
                        val socket = Socket(ip, 9000)
                        writer = PrintWriter(socket.getOutputStream(), true)
                        status = "Connected to $ip"
                    } catch (e: Exception) {
                        status = "Reconnect failed: ${e.message}"
                    }
                }
            }) {
                Text("Reconnect")
            }
        }
        Spacer(Modifier.width(128.dp))
        Box {
            Button(onClick = { }, modifier = Modifier.size(100.dp).offset(y = -90.dp)) {
                GameButton("Y", "Y", "Y_UP", writer, scope)
            } // Top
            Button(onClick = { }, modifier = Modifier.size(100.dp).offset(y = 90.dp)) {
                GameButton("A", "A", "A_UP", writer, scope)
            } // Bottom
            Button(onClick = { }, modifier = Modifier.size(100.dp).offset(x = -90.dp)) {
                GameButton("X", "X", "X_UP", writer, scope)
            } // Left
            Button(onClick = { }, modifier = Modifier.size(100.dp).offset(x = 90.dp)) {
                GameButton("B", "B", "B_UP", writer, scope)
            } // Right
        }
    }
}