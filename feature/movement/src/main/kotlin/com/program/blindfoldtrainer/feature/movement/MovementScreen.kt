package com.program.blindfoldtrainer.feature.movement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.core.audio.Buzz
import com.program.blindfoldtrainer.core.audio.EyesFreeControls
import com.program.blindfoldtrainer.core.audio.EyesFreeRow
import com.program.blindfoldtrainer.core.audio.EyesFreeZone
import com.program.blindfoldtrainer.core.audio.HELPER_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MicrophoneZone
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.ZoneTone
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult

/**
 * Jedini modul **bez ekrana i na jednoj i na jedinoj prečki**.
 *
 * Ostali moduli imaju dva lica — tablu i zone — pa se ekran grana po osloncu.
 * Ovde grananja nema: uz tablu bi se odgovor pročitao umesto izračunao, pa tabla
 * ne postoji ni kao mogućnost. Ono što se menja je **srednji red zona**, jer se
 * dva zadatka ne pitaju isto.
 *
 * Mikrofon se otvara **po jednom polju**, kao i u ostalim modulima: jedan pokret
 * koji se nauči jednom vredi više od ušteđenog dodira.
 *
 * ```
 * ┌───────────────────────────────┐
 * │           MIKROFON            │   50%
 * ├───────────────┬───────────────┤
 * │    PONOVI     │ GOTOVO/STANJE │   25%
 * ├───────────────┴───────────────┤
 * │      ODUSTANI (dva puta)      │   25%
 * └───────────────────────────────┘
 * ```
 */
@Composable
fun MovementScreen(
    difficulty: Difficulty,
    /** Porudžbina puta; bez nje modul radi svoj zatečeni zadatak. */
    taskId: String? = null,
    onFinish: (SessionResult) -> Unit,
    /** Koliko krugova; bez porudžbine koliko težina kaže. */
    rounds: Int? = null,
    viewModel: MovementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    LaunchedEffect(difficulty, taskId) { viewModel.startOnce(difficulty, taskId, rounds) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    // Kod dometa se odgovor mora **zaključiti**, jer je „nijedno" valjan odgovor
    // koji se ne vidi po broju izgovorenih polja. Kod šetnje se umesto toga nudi
    // čitanje stanja, jer aplikacija ćuti dok je tačno.
    val isReach = uiState.task == MovementTask.REACH

    EyesFreeControls(
        microphone = MicrophoneZone(
            isListening = voiceState == VoiceState.Listening,
            voiceState = voiceState,
            onToggle = {
                if (voiceState == VoiceState.Listening) viewModel.onVoiceStop()
                else viewModel.onVoiceInput()
            }
        ),
        rows = listOf(
            EyesFreeRow(
                weight = HELPER_ZONE_WEIGHT,
                zones = listOf(
                    EyesFreeZone(
                        label = "PONOVI",
                        tone = ZoneTone.SECONDARY,
                        // Kod dometa „ponovi" znači i **šta si dosad rekao**, ne
                        // samo pitanje: srednji red ovde nema mesta za treću
                        // zonu, a bez čitanja odgovora se ne bi imalo šta
                        // proveriti pre nego što se pritisne GOTOVO.
                        onClick = if (isReach) viewModel::onReadState else viewModel::onRepeat
                    ),
                    if (isReach) {
                        EyesFreeZone(
                            label = "GOTOVO",
                            tone = ZoneTone.PRIMARY,
                            buzz = Buzz.MEDIUM,
                            onClick = viewModel::onAnswerDone,
                            // Izgovoreno polje se ne može povući, pa dug dodir
                            // briše ceo odgovor i vraća pitanje.
                            onLongClick = viewModel::onAnswerClear
                        )
                    } else {
                        EyesFreeZone(
                            label = "STANJE",
                            tone = ZoneTone.TERTIARY,
                            buzz = Buzz.MEDIUM,
                            onClick = viewModel::onReadState
                        )
                    }
                )
            ),
            EyesFreeRow(
                weight = HELPER_ZONE_WEIGHT,
                zone = EyesFreeZone(
                    label = "ODUSTANI  (dva dodira)",
                    fontSize = 16.sp,
                    onClick = viewModel::onGiveUp,
                    onArmed = viewModel::onGiveUpArmed
                )
            )
        ),
        modifier = Modifier.padding(8.dp)
    )
}
