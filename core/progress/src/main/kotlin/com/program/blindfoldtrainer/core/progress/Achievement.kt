package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty

/**
 * Dostignuća.
 *
 * Kao i poeni, **izvedena su iz istorije i nigde se ne pamte**. Zato ne postoji
 * stanje koje može da se razmimoiđe sa stvarnošću: nema „osvojeno a ne piše" ni
 * obrnuto, a dodavanje novog dostignuća ga odmah prizna svima koji su ga već
 * zaslužili.
 *
 * U `BrainTrainer`-u su se dostignuća upisivala u `SharedPreferences` u trenutku
 * osvajanja, pa je novo dostignuće važilo samo za nove igrače.
 *
 * Nazivi za prikaz stoje u resursima `:app`-a — ovaj modul ne zna za jezik.
 */
enum class Achievement {
    /** Prva završena sesija. */
    FIRST_SESSION,

    /** Prva sesija bez ijedne greške. */
    FIRST_PERFECT,

    /** Deset besprekornih sesija ukupno. */
    TEN_PERFECT,

    /** Pet besprekornih sesija zaredom. */
    PERFECT_STREAK_FIVE,

    /** Besprekorna sesija na teškoj težini. */
    PERFECT_ON_HARD,

    /** Sto rešenih zadataka. */
    SOLVED_HUNDRED,

    /** Petsto rešenih zadataka. */
    SOLVED_FIVE_HUNDRED,

    /** Probana tri različita modula. */
    THREE_MODULES,

    /** Sat vremena treninga ukupno. */
    HOUR_OF_TRAINING,

    /** Dostignut rang Majstor. */
    RANK_MASTER;

    fun isEarnedIn(snapshot: ProgressSnapshot): Boolean = when (this) {
        FIRST_SESSION -> snapshot.sessions >= 1
        FIRST_PERFECT -> snapshot.perfectSessions >= 1
        TEN_PERFECT -> snapshot.perfectSessions >= 10
        PERFECT_STREAK_FIVE -> snapshot.bestPerfectStreak >= 5
        PERFECT_ON_HARD -> (snapshot.perfectByDifficulty[Difficulty.HARD] ?: 0) >= 1
        SOLVED_HUNDRED -> snapshot.solved >= 100
        SOLVED_FIVE_HUNDRED -> snapshot.solved >= 500
        THREE_MODULES -> snapshot.startedModules.size >= 3
        HOUR_OF_TRAINING -> snapshot.timeMillis >= HOUR_MILLIS
        RANK_MASTER -> snapshot.rank.ordinal >= Rank.MASTER.ordinal
    }

    companion object {
        private const val HOUR_MILLIS = 60L * 60L * 1_000L

        fun earnedIn(snapshot: ProgressSnapshot): Set<Achievement> =
            entries.filterTo(mutableSetOf()) { it.isEarnedIn(snapshot) }
    }
}
