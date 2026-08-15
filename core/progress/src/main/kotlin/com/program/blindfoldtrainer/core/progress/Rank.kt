package com.program.blindfoldtrainer.core.progress

/**
 * Lestvica rangova. Prag je ukupan broj poena.
 *
 * Rang ovde **ništa ne otključava**. U `BrainTrainer`-u je rang držao i spisak
 * dostupnih modula i težina, pa se nije moglo probati ono što te zanima dok ne
 * odradiš ono što te ne zanima. Da li i ovde uvoditi zaključavanje je zasebna
 * odluka; dok se ne donese, rang je samo oznaka napretka.
 *
 * Nazivi za prikaz stoje u resursima `:app`-a — ovaj modul je čist Kotlin i ne
 * zna za jezik ni za Android.
 */
enum class Rank(val requiredXp: Int) {
    BEGINNER(0),
    STUDENT(1_000),
    AMATEUR(3_000),
    EXPERIENCED(7_000),
    MASTER(15_000),
    GRANDMASTER(30_000);

    /** Sledeći rang, ili `null` ako je ovo vrh lestvice. */
    val next: Rank? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forXp(xp: Int): Rank {
            val safeXp = xp.coerceAtLeast(0)
            return entries.last { safeXp >= it.requiredXp }
        }
    }
}

/**
 * Koliko je pređeno unutar tekućeg ranga. Na vrhu lestvice [next] je `null`,
 * a [fraction] ostaje 1 — traka je puna i tu i staje.
 */
data class RankProgress(
    val current: Rank,
    val next: Rank?,
    val xpIntoRank: Int,
    val xpNeededForNext: Int
) {
    val fraction: Float
        get() = if (next == null || xpNeededForNext <= 0) 1f
        else (xpIntoRank.toFloat() / xpNeededForNext).coerceIn(0f, 1f)

    companion object {
        fun forXp(xp: Int): RankProgress {
            val safeXp = xp.coerceAtLeast(0)
            val current = Rank.forXp(safeXp)
            val next = current.next
            return RankProgress(
                current = current,
                next = next,
                xpIntoRank = safeXp - current.requiredXp,
                xpNeededForNext = if (next == null) 0 else next.requiredXp - current.requiredXp
            )
        }
    }
}
