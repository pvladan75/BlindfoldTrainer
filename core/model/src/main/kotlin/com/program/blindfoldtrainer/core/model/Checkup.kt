package com.program.blindfoldtrainer.core.model

/**
 * **Provera** — kratko merenje jedne veštine, uvek isto.
 *
 * Postoji zato što se učinak iz raznih modula ne sme sabrati u jedan broj:
 * pitanje u Geometriji traje dve sekunde, pozicija u Završnici tri minuta, a oba
 * su „jedan pokušaj". Sesije zato daju **napredak** unutar istog zadatka, a
 * poređiv **nivo** može da da samo merenje koje je svima jednako.
 *
 * Četiri svojstva bez kojih provera ne bi merila ono zbog čega postoji:
 *
 * - **uvek ista** — isti zadatak, ista težina, ista prečka, pa se rezultat sme
 *   porediti sa prošlim merenjem;
 * - **uz visoku podršku** — kad podrška padne, veštine prestaju da budu
 *   razdvojive: promašaj bez table ne kaže da li je otkazala veština koja se
 *   meri ili se pozicija iscurela. Dijagnoza traži razdvojivost, a teret ide u
 *   vežbu;
 * - **bez poena** — čim nosi poene, prestaje da meri i počne da se juri;
 * - **kratka** — merenje koje se izbegava ne meri ništa.
 *
 * Provera se radi **po jednoj veštini**, ne za svih osam odjednom: osam merenja
 * ne stane u tri minuta, a jedno stane u jedan. Profil se time popunjava u
 * komadima, i to je pošteno — veština koja nije proverena stoji kao „nije
 * mereno".
 */
data class Checkup(
    val skill: Skill,
    val moduleId: ModuleId,
    val taskId: String,
    /** Uvek ista težina, da bi dva merenja bila uporediva. */
    val difficulty: Difficulty,
    /** Uvek visoka podrška — dijagnoza traži da veštine budu razdvojive. */
    val support: Support = Support.FULL,
    /**
     * Koliko krugova, ako težina daje predugu sesiju. `null` — koliko modul da.
     *
     * Kratkoća je svojstvo provere, ne težine: merenje koje se izbegava ne meri
     * ništa. Gde je najlakša težina već dovoljno kratka, ovo ostaje prazno.
     */
    val rounds: Int? = null
)

/**
 * Provere koje postoje.
 *
 * **Nema ih za svih osam veština, i to se ne krije.** Veština bez provere ostaje
 * na „nije mereno", što je tačno stanje.
 *
 * Ono što fali, fali zato što veštinu **nijedan zadatak ne meri** — a ne zato
 * što merenje nije upisano:
 *
 * - **Oporavak slike** i **računanje naslepo** nemaju proveru zato što ih
 *   nijedan zadatak ne meri. Tu ne fali provera nego **vežba**, a to je nov
 *   modul, ne unos u ovaj spisak.
 */
object Checkups {

    val ALL: List<Checkup> = listOf(
        // Deset pitanja o boji polja, bez sata — oko četrdesetak sekundi.
        Checkup(
            skill = Skill.COORDINATES,
            moduleId = ModuleId.GEOMETRY,
            taskId = "square_color",
            difficulty = Difficulty.EASY
        ),

        // Osam putanja skakača na rastojanju dva — oko dva minuta.
        Checkup(
            skill = Skill.PIECE_GEOMETRY,
            moduleId = ModuleId.KNIGHT_PATH,
            taskId = "shortest_path",
            difficulty = Difficulty.EASY
        ),

        // Pet pozicija od po tri figure, sa šest sekundi gledanja.
        Checkup(
            skill = Skill.POSITION_HOLD,
            moduleId = ModuleId.RECALL,
            taskId = "reconstruct",
            difficulty = Difficulty.EASY
        ),

        // Pet pitanja „gde stoji figura" na razmaku od četiri poteza — oko dva
        // minuta, isti red veličine kao putanja skakača.
        //
        // Ovde se **uz tablu** ne znači da se pozicija vidi: u „Prati partiju" je
        // tabla prazna na svakoj prečki, a razlika je u tome da li potez piše na
        // ekranu ili se samo čuje. Visoka podrška je zato baš ono što provera
        // traži — meri se ažuriranje slike, a ne to koliko dobro čuješ.
        Checkup(
            skill = Skill.POSITION_UPDATE,
            moduleId = ModuleId.FOLLOW_GAME,
            taskId = "where_is_piece",
            difficulty = Difficulty.EASY
        ),

        // Pet pitanja „ko napada ovu figuru" — isti ulaz kao gore, druga veština.
        // Broj napadača se kaže unapred, pa se ne meri i pogađanje trenutka kad je
        // odgovor gotov.
        Checkup(
            skill = Skill.SQUARE_CONTROL,
            moduleId = ModuleId.FOLLOW_GAME,
            taskId = "attackers",
            difficulty = Difficulty.EASY
        ),

        // Dve izdiktirane pozicije od po tri figure — oko dva i po minuta.
        //
        // Najlakša težina daje pet pozicija, dakle oko šest minuta, a lakšeg nema:
        // manje od tri figure ne bi merilo isto. Zato provera ovde skraćuje sesiju
        // umesto da traži novu težinu — dužina je svojstvo merenja, težina je
        // svojstvo vežbe.
        Checkup(
            skill = Skill.NOTATION,
            moduleId = ModuleId.DICTATION,
            taskId = "place_position",
            difficulty = Difficulty.EASY,
            rounds = 2
        )
    )

    fun forSkill(skill: Skill): Checkup? = ALL.find { it.skill == skill }

    /** Veštine za koje provera postoji. Ostale se ne mogu ni ponuditi. */
    val measurableSkills: Set<Skill> = ALL.mapTo(mutableSetOf()) { it.skill }
}
