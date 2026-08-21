package com.program.blindfoldtrainer.feature.movement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
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
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support

/**
 * Ekran ima **dva lica: tablu i zone**, ali granica nije tamo gde se očekuje.
 *
 * Tabla se pojavljuje na tri načina, i samo je jedan od njih pomoć:
 *
 * - posle šetnje, da pokaže kuda si prošao — to je **odgovor**;
 * - u „Prepričaj putanju", dok se putanja crta — to je **pitanje**;
 * - u šetnji dok se radi — **nikad**, jer bi se odgovor pročitao umesto
 *   izračunao.
 *
 * Zone se javljaju kad je red na tebe, a njihov srednji red zavisi od zadatka:
 *
 * ```
 * ┌───────────────────────────────┐
 * │           MIKROFON            │   50%
 * ├───────────────┬───────────────┤
 * │    PONOVI     │ GOTOVO/STANJE │   25%
 * ├───────────────┴───────────────┤
 * │      ODUSTANI (dva dodira)    │   25%
 * └───────────────────────────────┘
 * ```
 *
 * Mikrofon se otvara **po jednom polju**, kao i u ostalim modulima: jedan pokret
 * koji se nauči jednom vredi više od ušteđenog dodira.
 */
@Composable
fun MovementScreen(
    difficulty: Difficulty,
    /** Porudžbina puta; bez nje modul radi svoj zatečeni zadatak. */
    taskId: String? = null,
    /** Porudžbina puta; bez nje zadatak uzima svoju najlakšu prečku. */
    support: Support? = null,
    onFinish: (SessionResult) -> Unit,
    /** Koliko krugova; bez porudžbine koliko težina kaže. */
    rounds: Int? = null,
    viewModel: MovementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    LaunchedEffect(difficulty, taskId) {
        viewModel.startOnce(difficulty, taskId, support, rounds)
    }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    uiState.replay?.let { replay ->
        WalkReplay(replay = replay, onContinue = viewModel::onReplayDone)
        return
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

/**
 * Odrađena šetnja, prikazana natrag.
 *
 * Figura stoji na polju do kog je prikaz stigao, a **potrošena polja iza nje
 * ostaju obojena** — iz njih se vidi oblik cele putanje, dok se redosled vidi iz
 * toga što prikaz korača. Strelice bi rekle isto, a tabla ih ne ume crtati.
 *
 * Dugme stoji **sve vreme**, ne tek na kraju: ko je video dovoljno ne mora da
 * čeka ostatak.
 */
@Composable
private fun WalkReplay(replay: Replay, onContinue: () -> Unit) {
    val board = Board.EMPTY.withPiece(replay.current, Piece(replay.piece, Color.WHITE))
    val tints = buildMap {
        replay.behind.forEach { square -> put(square, SquareTint.HINT) }
        put(replay.current, SquareTint.SUCCESS)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = if (replay.isPrompt) "Zapamti putanju" else "Tvoja šetnja",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        ChessBoard(board = board, tints = tints)

        Text(
            text = "${replay.step + 1} / ${replay.path.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Pitanje se ne preskače: dugme bi preskočilo baš ono što treba videti.
        if (!replay.isPrompt) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(if (replay.isDone) "DALJE" else "PRESKOČI")
            }
        }
    }
}
