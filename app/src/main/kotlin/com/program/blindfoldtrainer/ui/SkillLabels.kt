package com.program.blindfoldtrainer.ui

import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.Support
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
    "play_out" -> R.string.task_play_out
    "reconstruct" -> R.string.task_reconstruct
    "place_position" -> R.string.task_place_position
    else -> R.string.task_unknown
}
