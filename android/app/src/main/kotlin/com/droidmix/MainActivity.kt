package com.droidmix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

val BG = Color(0xFF0D0D0D)
val Surface1 = Color(0xFF1A1A1A)
val Surface2 = Color(0xFF242424)
val Accent = Color(0xFFFF5F2E)
val TextPri = Color(0xFFEEEEEE)
val TextSec = Color(0xFF888888)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            IEMMixApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Surface2,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSec,
    focusedTextColor = TextPri,
    unfocusedTextColor = TextPri,
    cursorColor = Accent
)

@Composable
fun Card(bg: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextSec,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    fmt: String,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = TextSec, fontSize = 12.sp, modifier = Modifier.width(140.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Surface2
            )
        )
        Text(
            text = String.format(fmt, value),
            color = TextPri,
            fontSize = 11.sp,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun StatusDot(connected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "StatusDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaAnimation"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (connected) Color(0xFF2EFF8A).copy(alpha = alpha) else TextSec
            )
    )
}

@Composable
fun VuMeter(level: Float) {
    val animLevel by animateFloatAsState(targetValue = level, animationSpec = tween(30), label = "vuAnim")
    
    val color = when {
        animLevel > 0.85f -> Color(0xFFFF3A3A)
        animLevel > 0.65f -> Color(0xFFFFCC00)
        else -> Accent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Surface2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = animLevel.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IEMMixApp() {
    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("9000") }
    var statusMsg by remember { mutableStateOf("Idle") }
    var isConnected by remember { mutableStateOf(false) }
    var vuLevel by remember { mutableFloatStateOf(0f) }

    var eqLow by remember { mutableFloatStateOf(0f) }
    var eqMid by remember { mutableFloatStateOf(0f) }
    var eqHigh by remember { mutableFloatStateOf(0f) }
    var compThresh by remember { mutableFloatStateOf(-20f) }
    var compRatio by remember { mutableFloatStateOf(4f) }
    var compMakeup by remember { mutableFloatStateOf(6f) }
    var gateThresh by remember { mutableFloatStateOf(-50f) }
    var inputGain by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(eqLow, eqMid, eqHigh, compThresh, compRatio, compMakeup, gateThresh, inputGain) {
        AudioEngine.dsp.eqLowGainDb = eqLow.toDouble()
        AudioEngine.dsp.eqMidGainDb = eqMid.toDouble()
        AudioEngine.dsp.eqHighGainDb = eqHigh.toDouble()
        AudioEngine.dsp.compThresholdDb = compThresh.toDouble()
        AudioEngine.dsp.compRatio = compRatio.toDouble()
        AudioEngine.dsp.compMakeupGainDb = compMakeup.toDouble()
        AudioEngine.dsp.gateThresholdDb = gateThresh.toDouble()
        AudioEngine.dsp.inputGainDb = inputGain.toDouble()
    }

    LaunchedEffect(isConnected) {
        while (isActive && isConnected) {
            vuLevel = AudioEngine.peakLinear
            delay(30)
        }
        if (!isConnected) {
            vuLevel = 0f
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BG)
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DroidMix", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    StatusDot(isConnected)
                }
                
                VuMeter(vuLevel)
                
                Card(bg = Surface1) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Connection")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("IP Address") },
                                colors = textFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Port") },
                                colors = textFieldColors(),
                                modifier = Modifier.width(90.dp)
                            )
                        }
                        Button(
                            onClick = {
                                if (!isConnected) {
                                    isConnected = true
                                    statusMsg = "Connecting…"
                                    AudioEngine.start(host, port.toIntOrNull() ?: 9000) { err ->
                                        isConnected = false
                                        statusMsg = "Error: $err"
                                    }
                                    if (isConnected) statusMsg = "Streaming"
                                } else {
                                    AudioEngine.stop()
                                    isConnected = false
                                    statusMsg = "Idle"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) Surface2 else Accent,
                                contentColor = if (isConnected) Accent else Color.Black
                            )
                        ) {
                            Text(if (isConnected) "⏹  Stop" else "▶  Start Streaming")
                        }
                        Text(statusMsg, color = TextSec, fontSize = 12.sp)
                    }
                }

                Card(bg = Surface1) {
                    Column {
                        SectionLabel("Input Gain")
                        SliderRow("Pre-DSP Gain", inputGain, -20f..20f, "%+.1f dB") { inputGain = it }
                    }
                }

                Card(bg = Surface1) {
                    Column {
                        SectionLabel("Equalizer")
                        SliderRow("Low Shelf (120 Hz)", eqLow, -18f..18f, "%+.1f dB") { eqLow = it }
                        SliderRow("Mid Peak (1 kHz)", eqMid, -18f..18f, "%+.1f dB") { eqMid = it }
                        SliderRow("High Shelf (8 kHz)", eqHigh, -18f..18f, "%+.1f dB") { eqHigh = it }
                    }
                }

                Card(bg = Surface1) {
                    Column {
                        SectionLabel("Compressor")
                        SliderRow("Threshold", compThresh, -60f..0f, "%.1f dB") { compThresh = it }
                        SliderRow("Ratio", compRatio, 1f..20f, "%.1f:1") { compRatio = it }
                        SliderRow("Makeup Gain", compMakeup, 0f..24f, "+%.1f dB") { compMakeup = it }
                    }
                }

                Card(bg = Surface1) {
                    Column {
                        SectionLabel("Noise Gate")
                        SliderRow("Threshold", gateThresh, -80f..0f, "%.1f dB") { gateThresh = it }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
