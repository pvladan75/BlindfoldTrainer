package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult

/**
 * Bodovanje jedne sesije.
 *
 * Namerno je čista funkcija na jednom mestu. Zahvaljujući tome se poeni ne
 * pamte uz zapis sesije, nego se **računaju iz sirovih rezultata pri svakom
 * čitanju** — pa promena pravila prepravi i celu dosadašnju istoriju, umesto da
 * ostavi staro bodovanje zamrznuto u bazi.
 *
 * U `BrainTrainer`-u se bodovanje zvalo iz desetak mesta u ekranu, pa se
 * pravilo nije moglo ni pročitati na jednom mestu ni testirati.
 */
object Xp {

    /** Poeni po rešenom zadatku, po težini. */
    fun perSolved(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 10
        Difficulty.MEDIUM -> 20
        Difficulty.HARD -> 35
    }

    /**
     * Poeni za sesiju: rešeni zadaci minus cena promašaja, uz dodatak za
     * besprekornu sesiju. Nikad manje od nule — trening ne sme da oduzima ono
     * što je ranije zarađeno.
     */
    fun forSession(result: SessionResult): Int {
        // Provera ne donosi poene. Merilo koje nosi nagradu prestaje da meri i
        // počne da se juri — a ono zbog čega provera postoji je da kaže istinu.
        if (result.isCheckup) return 0

        val earned = result.solved * perSolved(result.difficulty)
        val afterMistakes = (earned - result.mistakes * MISTAKE_COST).coerceAtLeast(0)

        // isPerfect podrazumeva nula promašaja, pa se dodatak računa na `earned`.
        return if (result.isPerfect) afterMistakes + earned * PERFECT_BONUS_PERCENT / 100 else afterMistakes
    }

    /** Koliko jedan promašaj skida. */
    const val MISTAKE_COST = 2

    /** Dodatak za sesiju bez ijedne greške, u procentima osnovice. */
    const val PERFECT_BONUS_PERCENT = 50
}
