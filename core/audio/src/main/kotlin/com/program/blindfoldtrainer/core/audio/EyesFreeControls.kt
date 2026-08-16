package com.program.blindfoldtrainer.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Vibracija po zoni. Različite dužine, da se **koja** je zona pogođena razazna
 * po osećaju — vibracija je jedina povratna informacija koja stiže pre govora.
 */
enum class Buzz(internal val millis: Long) {
    SHORT(20),
    MEDIUM(45),
    LONG(75),

    /** Dva kratka — za nepovratno. */
    DOUBLE(0)
}

/**
 * Boja zone. Postoji zbog onoga ko ipak pogleda; za samu vežbu je nebitna, pa
 * zone nose ton a ne konkretnu boju.
 */
enum class ZoneTone { PRIMARY, SECONDARY, TERTIARY, NEUTRAL }

/**
 * Jedna zona: velika meta koja se pogađa bez gledanja.
 *
 * [onArmed] razlikuje nepovratne radnje — kad je zadat, prvi dodir samo
 * najavljuje a tek drugi izvršava.
 */
data class EyesFreeZone(
    val label: String,
    val onClick: () -> Unit,
    val weight: Float = 1f,
    val tone: ZoneTone = ZoneTone.NEUTRAL,
    val buzz: Buzz = Buzz.SHORT,
    val onLongClick: (() -> Unit)? = null,
    val onArmed: (() -> Unit)? = null,
    val fontSize: TextUnit = 20.sp
)

/** Red zona. [weight] je udeo visine ekrana koji red zauzima. */
data class EyesFreeRow(val weight: Float, val zones: List<EyesFreeZone>) {
    constructor(weight: Float, zone: EyesFreeZone) : this(weight, listOf(zone))
}

/**
 * Mikrofon kao zona — uvek prva i najveća, jer se najviše koristi.
 *
 * Stoji odvojeno od ostalih zona zato što uz njega idu dozvola i objašnjenje
 * zašto glas ne radi; to je isto u svakom modulu i ne sme se prepisivati.
 */
data class MicrophoneZone(
    val isListening: Boolean,
    val voiceState: VoiceState,
    val onToggle: () -> Unit,
    val weight: Float = MAIN_ZONE_WEIGHT,
    val idleLabel: String = "MIKROFON"
)

/**
 * Podela visine ekrana: pola glavnoj zoni, po četvrtina pomoći i izlazu.
 *
 * Prva podela je bila 55 / 25 / 20 i sa uređaja je stiglo da su donje dve
 * pretanke — u njih se bez gledanja ne spušta prst nego se cilja, a to je
 * upravo ono što zone treba da uklone.
 */
const val MAIN_ZONE_WEIGHT = 0.50f
const val HELPER_ZONE_WEIGHT = 0.25f

/**
 * Upravljanje bez gledanja u ekran.
 *
 * Nisu dugmad nego **zone**: prst se ne cilja nego spusti, pa je svaka meta
 * velika i uvek na istom mestu.
 *
 * ```
 * ┌───────────────────────────────┐
 * │                               │
 * │           MIKROFON            │   50%
 * │                               │
 * ├───────────────┬───────────────┤
 * │    PONOVI     │   POZICIJA    │   25%
 * ├───────────────┴───────────────┤
 * │      ODUSTANI (dva puta)      │   25%
 * └───────────────────────────────┘
 * ```
 *
 * Raspored je isti u svim modulima: **gore ono što se traži sad** (mikrofon, ili
 * jedini odgovor koji modul očekuje), u sredini pomoć — ponavljanje i čitanje
 * stanja — a dole izlaz. Meta se tako pamti rukom, pa se prelazak iz modula u
 * modul ne uči ponovo.
 *
 * Pomoćne zone su namerno **ispod glavne, a ne na samom vrhu** — vrh ekrana
 * zauzimaju sat i otvor za kameru, pa se tamo bez gledanja ne pogađa. Iz istog
 * razloga se poštuju sistemske ivice.
 *
 * Dok su zone na ekranu, **orijentacija je zaključana na portret**.
 */
@Composable
fun EyesFreeControls(
    rows: List<EyesFreeRow>,
    modifier: Modifier = Modifier,
    microphone: MicrophoneZone? = null
) {
    val context = LocalContext.current
    val buzz = rememberBuzz()

    LockPortrait()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // Traži se bezuslovno, i kad modul u ovom trenutku nema mikrofon: sastav
    // kompozicije ne sme da zavisi od faze vežbe.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) microphone?.onToggle?.invoke()
        else Toast.makeText(
            context,
            "Bez dozvole za mikrofon glasovni unos ne radi.",
            Toast.LENGTH_LONG
        ).show()
    }

    // Kad slušanje krene samo od sebe — drugi deo poteza, bez novog dodira —
    // vibracija je jedini znak da je mikrofon opet živ.
    val isListening = microphone?.isListening == true
    LaunchedEffect(isListening) {
        if (isListening) buzz(Buzz.LONG)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (microphone != null) {
            val unavailable = microphone.voiceState as? VoiceState.Unavailable
            Zone(
                zone = EyesFreeZone(
                    label = if (microphone.isListening) {
                        "SLUŠAM — DODIRNI DA STANE"
                    } else {
                        microphone.idleLabel
                    },
                    tone = if (microphone.isListening) ZoneTone.NEUTRAL else ZoneTone.PRIMARY,
                    fontSize = 26.sp,
                    onClick = {
                        when {
                            microphone.isListening -> microphone.onToggle()
                            unavailable != null -> Toast.makeText(
                                context,
                                "${unavailable.reason} — jezik i paket biraš u Podešavanjima.",
                                Toast.LENGTH_LONG
                            ).show()

                            hasPermission -> microphone.onToggle()
                            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ),
                color = if (microphone.isListening) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                buzz = buzz,
                modifier = Modifier.fillMaxWidth().weight(microphone.weight)
            )
        }

        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().weight(row.weight)) {
                row.zones.forEach { zone ->
                    Zone(
                        zone = zone,
                        color = zone.tone.color(),
                        buzz = buzz,
                        modifier = Modifier.weight(zone.weight)
                    )
                }
            }
        }
    }
}

/**
 * Drži ekran u portretu dok se vežba zatvorenih očiju.
 *
 * Zone su podeljene po **visini**, pa u pejzažu postanu niske trake u koje se
 * bez gledanja ne pogađa. Uz to bi okretanje telefona u ruci — a on se tako i
 * drži — usred vežbe premestilo sve mete.
 *
 * Zaključava se ovde, a ne u manifestu, jer se odnosi samo na ovaj režim:
 * ostatak aplikacije se gleda i sme da se okreće. Zatečena vrednost se pamti i
 * vraća pri izlasku, da modul ne ostavi aplikaciju zaključanu za sobom.
 */
@Composable
private fun LockPortrait() {
    val activity = LocalActivity.current ?: return

    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = previous }
    }
}

@Composable
private fun ZoneTone.color(): Color = when (this) {
    ZoneTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    ZoneTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    ZoneTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
    ZoneTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Zone(
    zone: EyesFreeZone,
    color: Color,
    buzz: (Buzz) -> Unit,
    modifier: Modifier = Modifier
) {
    // Naoružana potvrda traje kratko: ako drugi dodir ne stigne, zaboravi se.
    var armedAtMillis by remember { mutableLongStateOf(0L) }
    val onArmed = zone.onArmed

    Box(
        modifier = modifier
            .padding(3.dp)
            .background(color, MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = {
                    when {
                        onArmed == null -> {
                            buzz(zone.buzz)
                            zone.onClick()
                        }

                        System.currentTimeMillis() - armedAtMillis < ARM_WINDOW_MILLIS -> {
                            armedAtMillis = 0
                            buzz(Buzz.DOUBLE)
                            zone.onClick()
                        }

                        else -> {
                            armedAtMillis = System.currentTimeMillis()
                            buzz(zone.buzz)
                            onArmed()
                        }
                    }
                },
                onLongClick = zone.onLongClick?.let { longClick ->
                    {
                        armedAtMillis = 0
                        buzz(Buzz.MEDIUM)
                        longClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = zone.label,
            fontSize = zone.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * Vibracija po zoni — različite dužine, da se dodir razlikuje po osećaju.
 *
 * Vibracija je jedina povratna informacija koja stiže **pre** govora; bez nje se
 * ne zna da li je dodir uopšte primljen dok TTS ne progovori.
 */
@Composable
private fun rememberBuzz(): (Buzz) -> Unit {
    val context = LocalContext.current
    val vibrator = remember(context) { context.vibrator() }

    return remember(vibrator) {
        { kind ->
            runCatching {
                when (kind) {
                    Buzz.DOUBLE -> vibrator?.vibrate(
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
