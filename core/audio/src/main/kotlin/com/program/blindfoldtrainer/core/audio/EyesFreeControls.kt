package com.program.blindfoldtrainer.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Upravljanje bez gledanja u ekran.
 *
 * Nisu dugmad nego **zone**: prst se ne cilja nego spusti, pa je svaka meta
 * velika i uvek na istom mestu. Mikrofon je najveći jer se koristi najviše.
 *
 * ```
 * ┌───────────────┬───────────────┐
 * │    PONOVI     │   POZICIJA    │
 * ├───────────────┴───────────────┤
 * │           MIKROFON            │
 * ├───────────────────────────────┤
 * │      ODUSTANI (dva puta)      │
 * └───────────────────────────────┘
 * ```
 *
 * Svaka zona vibrira drugačije, pa se pogodak prepozna **pre** nego što se išta
 * izgovori. Odustajanje traži dva dodira, jer je jedino nepovratno.
 */
@Composable
fun EyesFreeControls(
    isListening: Boolean,
    onMicrophone: () -> Unit,
    onRepeat: () -> Unit,
    onReadPosition: () -> Unit,
    onGiveUpArmed: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier,
    voiceState: VoiceState = VoiceState.Idle
) {
    val context = LocalContext.current
    val buzz = rememberBuzz()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) onMicrophone()
        else Toast.makeText(
            context,
            "Bez dozvole za mikrofon glasovni unos ne radi.",
            Toast.LENGTH_LONG
        ).show()
    }

    // Naoružano odustajanje traje kratko: ako se drugi dodir ne desi, zaboravi se.
    var armedAtMillis by remember { mutableLongStateOf(0L) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().weight(0.22f)) {
            Zone(
                label = "PONOVI",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = {
                    buzz(BuzzKind.SHORT)
                    onRepeat()
                }
            )
            Zone(
                label = "POZICIJA",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = {
                    buzz(BuzzKind.MEDIUM)
                    onReadPosition()
                }
            )
        }

        Zone(
            label = if (isListening) "SLUŠAM — DODIRNI DA STANE" else "MIKROFON",
            modifier = Modifier.fillMaxWidth().weight(0.58f),
            color = if (isListening) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            fontSize = 26.sp,
            onClick = {
                buzz(BuzzKind.LONG)
                when {
                    isListening -> onMicrophone()
                    voiceState is VoiceState.Unavailable -> Toast.makeText(
                        context,
                        "${voiceState.reason} — jezik i paket biraš u Podešavanjima.",
                        Toast.LENGTH_LONG
                    ).show()

                    hasPermission -> onMicrophone()
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )

        Zone(
            label = "ODUSTANI",
            modifier = Modifier.fillMaxWidth().weight(0.20f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            fontSize = 18.sp,
            onClick = {
                val now = System.currentTimeMillis()
                if (now - armedAtMillis < ARM_WINDOW_MILLIS) {
                    armedAtMillis = 0
                    buzz(BuzzKind.DOUBLE)
                    onGiveUp()
                } else {
                    armedAtMillis = now
                    buzz(BuzzKind.SHORT)
                    onGiveUpArmed()
                }
            }
        )
    }
}

@Composable
private fun Zone(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .background(color, MaterialTheme.shapes.medium)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp)
        )
    }
}

private enum class BuzzKind(val millis: Long) {
    SHORT(20),
    MEDIUM(45),
    LONG(75),
    DOUBLE(0)
}

/**
 * Vibracija po zoni — različite dužine, da se dodir razlikuje po osećaju.
 *
 * Vibracija je jedina povratna informacija koja stiže **pre** govora; bez nje se
 * ne zna da li je dodir uopšte primljen dok TTS ne progovori.
 */
@Composable
private fun rememberBuzz(): (BuzzKind) -> Unit {
    val context = LocalContext.current
    val vibrator = remember(context) { context.vibrator() }

    return remember(vibrator) {
        { kind ->
            runCatching {
                when (kind) {
                    BuzzKind.DOUBLE -> vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
                    )

                    else -> vibrator?.vibrate(
                        VibrationEffect.createOneShot(
                            kind.millis,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                }
            }
        }
    }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private const val ARM_WINDOW_MILLIS = 4_000L
