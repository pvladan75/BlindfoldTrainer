package com.program.blindfoldtrainer.core.model

/**
 * Jedinstveni identifikator modula za trening.
 *
 * Vrednost [key] se koristi kao ruta u navigaciji i kao prefiks pri čuvanju
 * napretka, pa se **ne sme menjati** kad modul jednom izađe u produkciju —
 * promena bi obrisala korisnikov napredak za taj modul.
 */
enum class ModuleId(val key: String) {
    /** Dobijena pozicija protiv Stockfish-a, naslepo. */
    ENDGAME("endgame"),

    /** Niz poteza stiže glasom, korisnik pokazuje polje na kojem se figure sreću. */
    PAIRS("pairs"),

    /** Boja polja, odnos dva polja, šta leži između. */
    GEOMETRY("geometry"),

    /** Pozicija se vidi ograničeno vreme, pa se rekonstruiše. */
    RECALL("recall"),

    /** Skakač od polazišta do odredišta, bez table. */
    KNIGHT_PATH("knight_path"),

    /** Potezi majstorske partije stižu jedan po jedan, uz povremena pitanja. */
    FOLLOW_GAME("follow_game"),

    /**
     * Pozicija se izgovara, a slaže se na tabli. Jedini modul koji ide **od
     * zapisa ka slici u glavi**; ostali idu obrnuto.
     */
    DICTATION("dictation");

    companion object {
        fun fromKey(key: String): ModuleId? = entries.find { it.key == key }
    }
}

enum class Difficulty { EASY, MEDIUM, HARD }

/**
 * Šta modulu treba od školjke da bi radio. Školjka na osnovu ovoga traži
 * dozvole i priprema resurse **pre** nego što uđeš u modul, umesto da svaki
 * modul sam petlja sa dozvolama.
 */
enum class Capability {
    /** Prepoznavanje govora (Vosk) — traži RECORD_AUDIO dozvolu. */
    VOICE_INPUT,

    /** Izgovaranje poteza (TTS). */
    SPEECH_OUTPUT,

    /** Stockfish. Učitavanje engine-a je skupo, pa se radi unapred. */
    ENGINE
}

/**
 * Koliko je jedna veština dobila u jednoj sesiji.
 *
 * Namerno **bez procenta**: procenat izgleda tačno a nije, jer je sastavljen od
 * nejednakih zadataka. Dva broja se sabiraju kroz istoriju i tek iz njih se
 * računa ono što se prikazuje.
 */
data class SkillTally(val attempted: Int, val solved: Int) {
    init {
        require(attempted >= 0) { "attempted ne može biti negativan" }
        require(solved in 0..attempted) { "solved ($solved) mora biti u 0..attempted ($attempted)" }
    }

    operator fun plus(other: SkillTally) =
        SkillTally(attempted + other.attempted, solved + other.solved)
}

/**
 * Ishod jedne završene sesije treninga.
 *
 * Ovo je **jedini** kanal kojim modul prijavljuje rezultat. Zahvaljujući tome
 * se bodovanje, rangovi i dostignuća pišu jednom u `:core:progress`, umesto da
 * svaki modul sam zove menadžere napretka.
 */
data class SessionResult(
    val moduleId: ModuleId,
    val difficulty: Difficulty,
    /** Koliko je zadataka ponuđeno korisniku. */
    val attempted: Int,
    /** Koliko ih je rešeno. */
    val solved: Int,
    /** Ukupan broj pogrešnih pokušaja tokom sesije. */
    val mistakes: Int,
    val elapsedMillis: Long,
    /** Da li je sesija završena bez odustajanja (korisnik nije prekinuo). */
    val completed: Boolean = true,

    /**
     * Šta je sesija dodirnula, **po veštinama**.
     *
     * Zbirni brojevi iznad kažu koliko si dobro prošao; ovo kaže **koja veština
     * klizi**, a to je jedino po čemu se profil, provera i put uopšte mogu
     * napraviti. Sesija ostaje jedan red u istoriji — razlaganje je u njoj, a ne
     * u zasebnoj tabeli, jer se veština meri po sesiji a ne po dodiru.
     *
     * Prazno je dozvoljeno i **znači „nije mereno"**, ne nulu: sesije upisane
     * pre ove izmene ga nemaju, i to se korisniku tako i kaže.
     */
    val bySkill: Map<Skill, SkillTally> = emptyMap(),

    /**
     * Na kojoj je **prečki podrške** sesija odrađena.
     *
     * Bez ovoga se profil može naduvati: deset tačnih uz tablu i deset tačnih
     * bez nje upisivali bi se istom težinom, a to nisu isti dokazi. Nivo veštine
     * je prečka koju drži, ne procenat — a prečka se ne zna ako se ne upiše.
     *
     * `null` znači **ne zna se**: sesije upisane pre ove izmene. Takve u profil
     * po veštinama ne ulaze, umesto da se pretvaraju da su bile na najlakšoj.
     */
    val support: Support? = null
) {
    init {
        require(attempted >= 0) { "attempted ne može biti negativan" }
        require(solved in 0..attempted) { "solved ($solved) mora biti u 0..attempted ($attempted)" }
        require(mistakes >= 0) { "mistakes ne može biti negativan" }
        require(elapsedMillis >= 0) { "elapsedMillis ne može biti negativan" }
    }

    /** Sve rešeno, bez ijedne greške. */
    val isPerfect: Boolean
        get() = completed && attempted > 0 && solved == attempted && mistakes == 0

    val accuracy: Float
        get() = if (attempted == 0) 0f else solved.toFloat() / attempted

    /** Veštine koje je ova sesija uopšte dodirnula. */
    val skills: Set<Skill> get() = bySkill.keys

    companion object {
        /** Sesija koju je korisnik napustio pre kraja. */
        fun abandoned(
            moduleId: ModuleId,
            difficulty: Difficulty,
            attempted: Int,
            solved: Int,
            mistakes: Int,
            elapsedMillis: Long
        ) = SessionResult(
            moduleId = moduleId,
            difficulty = difficulty,
            attempted = attempted,
            solved = solved,
            mistakes = mistakes,
            elapsedMillis = elapsedMillis,
            completed = false
        )
    }
}
