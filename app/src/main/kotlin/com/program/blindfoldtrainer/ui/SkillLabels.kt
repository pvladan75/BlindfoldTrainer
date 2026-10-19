package com.program.blindfoldtrainer.ui

import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.progress.Achievement
import com.program.blindfoldtrainer.core.progress.Rank
import com.program.blindfoldtrainer.core.progress.Reason

/**
 * Ime veštine na ekranu.
 *
 * Stoji u `:app`-u, uz ostale prevode ekrana, a ne uz `Skill` u modelu — model
 * ne zna za resurse, i to je razlog zbog kog uopšte može da se testira bez
 * Androida.
 */
internal fun Skill.labelRes(): Int = when (this) {
    Skill.COORDINATES -> R.string.skill_coordinates
    Skill.PIECE_GEOMETRY -> R.string.skill_piece_geometry
    Skill.POSITION_HOLD -> R.string.skill_position_hold
    Skill.POSITION_UPDATE -> R.string.skill_position_update
    Skill.NOTATION -> R.string.skill_notation
    Skill.RECOVERY -> R.string.skill_recovery
    Skill.SQUARE_CONTROL -> R.string.skill_square_control
    Skill.CALCULATION -> R.string.skill_calculation
}

/**
 * Šta veština znači, u jednoj rečenici.
 *
 * Bez ovoga je profil spisak stranih reči: „ažuriranje" ne kaže ništa dok se ne
 * kaže da je to ono što ti se raspada u desetom potezu.
 */
internal fun Skill.hintRes(): Int = when (this) {
    Skill.COORDINATES -> R.string.skill_coordinates_hint
    Skill.PIECE_GEOMETRY -> R.string.skill_piece_geometry_hint
    Skill.POSITION_HOLD -> R.string.skill_position_hold_hint
    Skill.POSITION_UPDATE -> R.string.skill_position_update_hint
    Skill.NOTATION -> R.string.skill_notation_hint
    Skill.RECOVERY -> R.string.skill_recovery_hint
    Skill.SQUARE_CONTROL -> R.string.skill_square_control_hint
    Skill.CALCULATION -> R.string.skill_calculation_hint
}

/**
 * Duži opis veštine — za uputstvo, ne za profil.
 *
 * Rečenica iz [hintRes] kaže **šta je** veština, i toliko staje uz ocenu na
 * kartici. Ovde stoji **kako izgleda kad otkaže**, jer se veština po tome i
 * prepoznaje: niko ne zna da mu curi držanje pozicije dok mu ne kažeš da se to
 * ne primećuje iznutra.
 */
internal fun Skill.guideRes(): Int = when (this) {
    Skill.COORDINATES -> R.string.skill_coordinates_guide
    Skill.PIECE_GEOMETRY -> R.string.skill_piece_geometry_guide
    Skill.POSITION_HOLD -> R.string.skill_position_hold_guide
    Skill.POSITION_UPDATE -> R.string.skill_position_update_guide
    Skill.NOTATION -> R.string.skill_notation_guide
    Skill.RECOVERY -> R.string.skill_recovery_guide
    Skill.SQUARE_CONTROL -> R.string.skill_square_control_guide
    Skill.CALCULATION -> R.string.skill_calculation_guide
}

/**
 * Šta prečka znači, u jednoj rečenici — za uputstvo.
 *
 * [labelRes] je ime koje staje na dugme; ovo je objašnjenje koje uz to ime ide
 * jednom, dok se ne shvati da prečka nije isto što i težina.
 */
internal fun Support.guideRes(): Int = when (this) {
    Support.FULL -> R.string.guide_rung_full
    Support.PARTIAL -> R.string.guide_rung_partial
    Support.TRACE -> R.string.guide_rung_trace
    Support.NONE -> R.string.guide_rung_none
}

/**
 * Ime prečke na ekranu.
 *
 * Ne kaže se „FULL" nego **šta korisnik vidi** — prečka je za njega opis vežbe,
 * a ne stepen na skali.
 */
internal fun Support.labelRes(): Int = when (this) {
    Support.FULL -> R.string.support_full
    Support.PARTIAL -> R.string.support_partial
    Support.TRACE -> R.string.support_trace
    Support.NONE -> R.string.support_none
}

/**
 * Zašto je baš ovo predloženo.
 *
 * Razlog je obavezan deo predloga: preporuka bez razloga je proročanstvo, a
 * proročanstvu se ne veruje kad promaši.
 */
internal fun Reason.labelRes(): Int = when (this) {
    Reason.NEVER_TRIED -> R.string.reason_never_tried
    Reason.WEAKEST -> R.string.reason_weakest
    Reason.FOUNDATION -> R.string.reason_foundation
    Reason.STRENGTH -> R.string.reason_strength
    Reason.UPKEEP -> R.string.reason_upkeep
}

/**
 * Ime vrste zadatka na ekranu.
 *
 * Profil se razlaže po zadacima, pa se zadatak mora i imenovati. Ključ dolazi iz
 * modula (`TaskSpec.id`), a ime stoji ovde — modul ne zna za resurse, kao ni
 * model.
 */
internal fun taskLabelRes(taskId: String): Int = when (taskId) {
    "square_color" -> R.string.task_square_color
    "shortest_path" -> R.string.task_shortest_path
    "meeting_square" -> R.string.task_meeting_square
    "where_is_piece" -> R.string.task_where_is_piece
    "attackers" -> R.string.task_attackers
    "safe_path" -> R.string.task_safe_path
    "no_capture" -> R.string.task_no_capture
    "play_out" -> R.string.task_play_out
    "reconstruct" -> R.string.task_reconstruct
    "place_position" -> R.string.task_place_position
    "reach_on_line" -> R.string.task_reach_on_line
    "retell_path" -> R.string.task_retell_path
    "walk_piece" -> R.string.task_walk_piece
    "walk_knight" -> R.string.task_walk_knight
    else -> R.string.task_unknown
}

internal fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

/**
 * Nazivi rangova stoje ovde, a ne u `:core:progress` — taj modul je čist Kotlin
 * i ne zna ni za jezik ni za resurse.
 */
internal fun Rank.labelRes(): Int = when (this) {
    Rank.BEGINNER -> R.string.rank_beginner
    Rank.STUDENT -> R.string.rank_student
    Rank.AMATEUR -> R.string.rank_amateur
    Rank.EXPERIENCED -> R.string.rank_experienced
    Rank.MASTER -> R.string.rank_master
    Rank.GRANDMASTER -> R.string.rank_grandmaster
}

internal fun Achievement.labelRes(): Int = when (this) {
    Achievement.FIRST_SESSION -> R.string.achievement_first_session
    Achievement.FIRST_PERFECT -> R.string.achievement_first_perfect
    Achievement.TEN_PERFECT -> R.string.achievement_ten_perfect
    Achievement.PERFECT_STREAK_FIVE -> R.string.achievement_perfect_streak_five
    Achievement.PERFECT_ON_HARD -> R.string.achievement_perfect_on_hard
    Achievement.SOLVED_HUNDRED -> R.string.achievement_solved_hundred
    Achievement.SOLVED_FIVE_HUNDRED -> R.string.achievement_solved_five_hundred
    Achievement.THREE_MODULES -> R.string.achievement_three_modules
    Achievement.HOUR_OF_TRAINING -> R.string.achievement_hour_of_training
    Achievement.RANK_MASTER -> R.string.achievement_rank_master
}
