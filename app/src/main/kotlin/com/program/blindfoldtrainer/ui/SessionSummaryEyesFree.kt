package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.audio.Buzz
import com.program.blindfoldtrainer.core.audio.EyesFreeControls
import com.program.blindfoldtrainer.core.audio.EyesFreeRow
import com.program.blindfoldtrainer.core.audio.EyesFreeZone
import com.program.blindfoldtrainer.core.audio.HELPER_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MAIN_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.ZoneTone
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.progress.SessionReward

/**
 * Sažetak sesije bez ekrana.
 *
 * Dijalog je do sada bio jedino mesto gde režim propada nazad na gledanje. Izlaz
 * je i tada postojao — dodir van dijaloga vraća u meni — ali se za njega nije
 * moglo **znati**, a „Još jednom" se nije moglo dohvatiti nikako. Za vežbu
 * zatvorenih očiju je to bio kraj rada: sesija se završi i dalje se ne može.
 *
 * ```
 * ┌───────────────────────────────┐
 * │          JOŠ JEDNOM           │   50%
 * ├───────────────────────────────┤
 * │        REZULTAT  8/10         │   25%
 * ├───────────────────────────────┤
 * │             MENI              │   25%
 * └───────────────────────────────┘
 * ```
 *
 * Raspored je onaj isti iz svih modula, pa se meta i ovde pamti rukom: gore ono
 * što se najčešće hoće — a posle jedne vežbe se najčešće hoće još jedna — u
 * sredini pomoć, dole izlaz.
 *
 * **Izlaz ovde ne traži dva dodira**, iako ga traži u vežbi. Dva dodira postoje
 * zbog nepovratnog: usred vežbe odustajanje baca sve što je urađeno. Ovde je
 * sesija gotova i **upisana pre nego što se sažetak pojavio**, pa se izlaskom
 * ništa ne gubi — potvrda bi bila obred bez razloga.
 */
@Composable
fun SessionSummaryEyesFree(
    result: SessionResult,
    reward: SessionReward?,
    onAnnounce: (String) -> Unit,
    onSay: (String) -> Unit,
    onRepeat: () -> Unit,
    onBackToMenu: () -> Unit
) {
    // Šta se sad može — jedino tako se za zone i sazna. Čeka svoj red iza
    // modulovog „Kraj sesije, rešeno toliko od toliko".
    val zones = stringResource(R.string.summary_eyes_free_zones)
    LaunchedEffect(Unit) { onAnnounce(zones) }

    val spokenResult = resultSpeech(result, reward)

    EyesFreeControls(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        rows = listOf(
            EyesFreeRow(
                weight = MAIN_ZONE_WEIGHT,
                zone = EyesFreeZone(
                    label = stringResource(R.string.summary_zone_again),
                    onClick = onRepeat,
                    tone = ZoneTone.PRIMARY,
                    fontSize = 26.sp
                )
            ),
            EyesFreeRow(
                weight = HELPER_ZONE_WEIGHT,
                zone = EyesFreeZone(
                    label = stringResource(
                        R.string.summary_zone_result,
                        result.solved,
                        result.attempted
                    ),
                    onClick = { onSay(spokenResult) },
                    tone = ZoneTone.SECONDARY,
                    buzz = Buzz.MEDIUM
                )
            ),
            EyesFreeRow(
                weight = HELPER_ZONE_WEIGHT,
                zone = EyesFreeZone(
                    label = stringResource(R.string.summary_zone_menu),
                    onClick = onBackToMenu,
                    tone = ZoneTone.NEUTRAL,
                    buzz = Buzz.LONG
                )
            )
        )
    )
}

/**
 * Rečenica koja se izgovori na zahtev.
 *
 * Poeni, rang i dostignuća su do sada postojali **samo u dijalogu** — ko vežba
 * zatvorenih očiju za njih nije znao. Sastavlja se ovde, a ne u ViewModel-u,
 * jer su imena rangova i dostignuća resursi.
 */
@Composable
private fun resultSpeech(result: SessionResult, reward: SessionReward?): String {
    val parts = mutableListOf(
        stringResource(
            R.string.summary_speech_result,
            result.solved,
            result.attempted,
            result.mistakes
        )
    )

    reward?.let {
        parts += stringResource(R.string.summary_speech_xp, it.xp)
        if (it.isRankUp) {
            parts += stringResource(R.string.summary_rank_up, stringResource(it.rankAfter.labelRes()))
        }
        it.newAchievements.forEach { achievement ->
            parts += stringResource(R.string.summary_achievement, stringResource(achievement.labelRes()))
        }
    }

    return parts.joinToString(" ")
}
