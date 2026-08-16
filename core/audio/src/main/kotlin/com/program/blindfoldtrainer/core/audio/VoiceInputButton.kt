package com.program.blindfoldtrainer.core.audio

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
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
 * **Dugme se ne skriva kad glas nije upotrebljiv.** Ranije jeste, pa se nije
 * moglo razaznati da li fali paket, dozvola, ili je samo pogrešan trenutak u
 * vežbi — a to je isti onaj nemi otkaz koji je u ovom projektu već dvaput skupo
 * koštao. Sada dodir kaže šta nedostaje.
 */
@Composable
fun VoiceInputButton(
    state: VoiceState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var wasDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        wasDenied = !granted

        if (granted) {
            // Dozvola stiže tek pošto je dugme već pritisnuto, pa se slušanje
            // pokreće ovde — inače bi prvi dodir uvek propao.
            onStartListening()
        } else {
            context.toast("Bez dozvole za mikrofon glasovni unos ne radi.")
        }
    }

    val isListening = state == VoiceState.Listening
    val isPreparing = state == VoiceState.Preparing
    val unavailable = (state as? VoiceState.Unavailable)?.reason

    Surface(
        onClick = {
            when {
                // Dok sluša, dodir gasi. Bez toga se slušanje nije moglo
                // prekinuti ničim — dugme je ćutalo, a mikrofon ostajao upaljen.
                isListening -> onStopListening()

                unavailable != null ->
                    context.toast("$unavailable — jezik i paket biraš u Podešavanjima.")

                isPreparing -> context.toast("Paket se još priprema.")

                wasDenied && !hasPermission ->
                    context.toast("Dozvoli mikrofon u podešavanjima telefona.")

                hasPermission -> onStartListening()

                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.size(52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = when {
            isListening -> MaterialTheme.colorScheme.errorContainer
            unavailable != null -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = when {
                        isListening -> "Slušam"
                        unavailable != null -> "Glasovni unos nije spreman"
                        else -> "Izgovori polje"
                    },
                    tint = when {
                        isListening -> MaterialTheme.colorScheme.onErrorContainer
                        unavailable != null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}

private fun android.content.Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
