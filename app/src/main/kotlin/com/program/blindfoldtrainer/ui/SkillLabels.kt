package com.program.blindfoldtrainer.ui

import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Skill

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
