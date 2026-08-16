package com.program.blindfoldtrainer.core.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Dugme za glasovni unos, zajedno sa traženjem dozvole.
 *
 * Stoji ovde, uz [VoiceState], a ne u svakom modulu: dozvola, stanja i ponašanje
 * pri odbijanju su isti svuda, a tri kopije bi se pre ili kasnije razišle.
 *
 * Kad glas nije upotrebljiv — model nije preuzet, ili je korisnik odbio dozvolu —
 * dugme se **ne prikazuje**. Bolje nego dugme koje ne radi ništa.
 */
@Composable
fun VoiceInputButton(
    state: VoiceState,
    onStartListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current

    var isDenied by remember { mutableStateOf(false) }
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
        isDenied = !granted
        // Dozvola stiže tek pošto je dugme već pritisnuto, pa se slušanje
        // pokreće ovde — inače bi prvi dodir uvek propao.
        if (granted) onStartListening()
    }

    if (state is VoiceState.Unavailable || isDenied) return

    val isListening = state == VoiceState.Listening
    val isPreparing = state == VoiceState.Preparing

    Surface(
        onClick = {
            if (hasPermission) {
                onStartListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.size(52.dp),
        enabled = enabled && !isPreparing,
        shape = MaterialTheme.shapes.medium,
        color = if (isListening) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isPreparing -> CircularProgressIndicator(modifier = Modifier.size(20.dp))

                else -> Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (isListening) "Slušam" else "Izgovori polje",
                    tint = if (isListening) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}
