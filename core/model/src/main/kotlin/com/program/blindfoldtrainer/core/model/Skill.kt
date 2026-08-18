package com.program.blindfoldtrainer.core.model

/**
 * Veština koju vežba razvija.
 *
 * **Veština je kičma, modul je alat.** Do sada su moduli bili polazište, a to
 * šta se njima razvija posledica; odavde je obrnuto — meri se i prati veština,
 * a modul je samo put do nje.
 *
 * Veština pripada **zadatku, ne modulu**. Isti modul sme da pita „gde je beli
 * skakač" i „koje crne figure napadaju skakača na e5" — isti ulaz, ista
 * podrška, a prvo je [POSITION_UPDATE] a drugo [SQUARE_CONTROL] na leđima
 * [POSITION_HOLD]. Zato veštine prijavljuje [TaskSpec], a modul samo skuplja
 * uniju svojih zadataka.
 *
 * Ključ se **ne sme menjati** kad modul jednom izađe u produkciju: po njemu se
 * čuva napredak, isto kao kod [ModuleId].
 */
enum class Skill(val key: String) {

    /** „e4" se ne računa — boja, susedi i dijagonale se znaju odmah. */
    COORDINATES("coordinates"),

    /** Sa datog polja se odmah vidi kuda figura ide; skakač je granica. */
    PIECE_GEOMETRY("piece_geometry"),

    /** Veze figura–polje se drže stabilno, bez curenja. */
    POSITION_HOLD("position_hold"),

    /**
     * Potez se primeni na sliku a da je ne pokvari.
     *
     * **Usko grlo, ne [POSITION_HOLD].** Statična pozicija se pamti relativno
     * lako; greška se gomila pri ažuriranju, jer svaki potez nosi priliku da
     * nešto ispadne, a greške se ne poništavaju nego slažu.
     */
    POSITION_UPDATE("position_update"),

    /** „g1 f3" se čuje i vidi; i obrnuto, viđeno se ume izgovoriti. */
    NOTATION("notation"),

    /**
     * Primetiti da se slika raspala i sastaviti je ponovo.
     *
     * Zasad se samo **meri** — broj čitanja u Diktatu, „Čitaj poziciju" u
     * Završnici — a nijedan zadatak je ne uči.
     */
    RECOVERY("recovery"),

    /**
     * Znati šta protivnik drži i koje je polje zato vruće.
     *
     * U pravoj partiji naslepo se figure ne gube zato što se zaboravi gde
     * stoje, nego zato što se zaboravi **šta drže**. Nijedan zadatak je još ne
     * dodiruje.
     */
    SQUARE_CONTROL("square_control"),

    /** Varijanta se vodi bez table. */
    CALCULATION("calculation")
}

/**
 * Veštine bez kojih ova ne može da napreduje.
 *
 * Razlog nije pedagoški nego mehanički: **radna memorija je jedna.** Ako
 * traženje polja e4 troši pažnju, nema se čime držati pozicija — pa držanje ne
 * napreduje ma koliko se vežbalo.
 *
 * Preduslov **ništa ne zaključava.** Ulazi samo u preporuku i u rečenicu na
 * kartici veštine; pogrešna procena se tako ignoriše, dok bi zaključavanje
 * ostavilo čoveka pred vratima. Isto pravilo po kom rang ništa ne otključava.
 *
 * ```
 * KOORDINATE ──┬──> DRŽANJE ──┬──> AŽURIRANJE ──┐
 *              │              │                 ├──> RAČUNANJE
 *              └──> ZAPIS     └──> OPORAVAK     │
 * GEOMETRIJA ──┬──> AŽURIRANJE                  │
 *              └──> KONTROLA POLJA ─────────────┘
 * ```
 */
val Skill.requires: Set<Skill>
    get() = when (this) {
        Skill.COORDINATES -> emptySet()
        Skill.PIECE_GEOMETRY -> emptySet()
        Skill.POSITION_HOLD -> setOf(Skill.COORDINATES)
        Skill.NOTATION -> setOf(Skill.COORDINATES)
        Skill.POSITION_UPDATE -> setOf(Skill.POSITION_HOLD, Skill.PIECE_GEOMETRY)
        Skill.RECOVERY -> setOf(Skill.POSITION_HOLD)
        Skill.SQUARE_CONTROL -> setOf(Skill.PIECE_GEOMETRY, Skill.POSITION_HOLD)
        Skill.CALCULATION -> setOf(Skill.POSITION_UPDATE, Skill.SQUARE_CONTROL)
    }

/**
 * Koliko slike aplikacija drži **umesto tebe**.
 *
 * Ovo je prava lestvica težine za vežbu naslepo. Dotadašnja [Difficulty] skalira
 * *koliko* i *koliko brzo* — to je napor, ne veština. Podrška skalira ono što
 * zadatak zaista traži od glave.
 *
 * ```
 * FULL          PARTIAL           TRACE            NONE
 * cela tabla    deo slike     samo tragovi       ništa
 * najlakše ────────────────────────────────────► najteže
 * ```
 *
 * **„Bez ekrana" je bio skok sa prve prečke na poslednju** — otud i osećaj da
 * neki modul bez ekrana ne može. Kao prečka unutar zadatka, prestaje da bude
 * režim kome modul „ne radi": zadatak ponudi najnižu prečku koju ume.
 *
 * Šta koja prečka **znači** zavisi od zadatka, i zadatak to i kaže; zajednički
 * je samo redosled. U „Prati partiju" je [PARTIAL] „vidi se figura koja se
 * pomera", a [TRACE] „vide se samo polja"; u Geometriji [FULL] znači da se
 * posle odgovora pokaže tabla sa poljem, a [NONE] da se istina samo izgovori.
 */
enum class Support(val key: String) {
    FULL("full"),
    PARTIAL("partial"),
    TRACE("trace"),
    NONE("none");

    /** Prečka niže, ili `null` ako se dalje ne može. */
    fun harder(): Support? = entries.getOrNull(ordinal + 1)

    /** Prečka više, ili `null` ako je već najlakša. */
    fun easier(): Support? = entries.getOrNull(ordinal - 1)
}

/**
 * **Orijentir** — rezultat kom se teži na jednoj prečki jednog zadatka.
 *
 * Nije „maksimum": nije gornja granica skale i sme da se pređe. To je tačka po
 * kojoj se čovek upravlja, a kad je pređe, prestaje da bude horizont i postaje
 * pod — preuzima ga orijentir sledeće prečke.
 *
 * **Par, a ne broj.** Da stoji samo vreme, merilo bi pozivalo na žurbu, a žurba
 * obara tačnost — koja je pola veštine. Priznaje se tek kad su ispunjena oba.
 *
 * Vreme je **ceo krug jednog zadatka**, ne čisto razmišljanje: u njemu su i
 * izgovor i pauza posle odgovora. Zato je orijentir na istoj prečki uvek
 * izdašniji nego što bi se očekivalo, a na težoj prečki i veći — bez ekrana se
 * ista stvar mora i izgovoriti.
 *
 * Brojevi su **prvi predlog**, kao i pragovi rangova: stoje na jednom mestu i
 * menjaju se bez diranja istorije.
 */
data class Benchmark(val millisPerAttempt: Long, val minAccuracy: Float) {
    init {
        require(millisPerAttempt > 0) { "orijentir bez vremena ne bi značio ništa" }
        require(minAccuracy in 0f..1f) { "tačnost je udeo, dobijeno $minAccuracy" }
    }
}

/**
 * Šta je jedna vrsta zadatka: šta pita, šta razvija i koliko podrške ume.
 *
 * Modul je **svežanj** ovakvih zadataka, a ne jedna vežba. Zato se veštine
 * prijavljuju ovde: bez toga bi se sve što modul radi svelo na jednu oznaku, a
 * upravo je razlika među pitanjima ono što razlikuje veštine.
 *
 * [id] je ključ unutar modula i **ne sme se menjati** kad izađe u produkciju,
 * jer po njemu put traži zadatak i po njemu se čuva napredak.
 */
data class TaskSpec(
    val id: String,
    /** Šta ovaj zadatak razvija. Prva je ona koju **meri**, ostale nosi uz nju. */
    val skills: List<Skill>,
    /**
     * Prečke koje ovaj zadatak ume, od najlakše ka najtežoj.
     *
     * Zadatak koji ume [Support.NONE] radi i zatvorenih očiju. Zadatak koji je
     * ne ume nije neispravan — samo ima nižu granicu, i školjka to kaže umesto
     * da korisnik sazna unutra.
     */
    val supports: List<Support>,

    /**
     * Kome se teži, po prečki. Prečka bez orijentira ga naprosto ne prikazuje.
     */
    val benchmarks: Map<Support, Benchmark> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "zadatak mora imati ključ" }
        require(skills.isNotEmpty()) { "zadatak bez veštine ne bi imao svrhu: $id" }
        require(supports.isNotEmpty()) { "zadatak mora imati bar jednu prečku: $id" }
    }

    /** Veština koju ovaj zadatak **meri** — po njoj ide u profil. */
    val measures: Skill get() = skills.first()

    /**
     * Najmanja podrška koju zadatak ume — dakle **najteža prečka** do koje može.
     *
     * Zadatak koji ovde ima [Support.NONE] radi i zatvorenih očiju.
     */
    val hardest: Support get() = supports.maxByOrNull { it.ordinal } ?: Support.FULL

    fun supports(support: Support): Boolean = support in supports

    fun benchmarkFor(support: Support): Benchmark? = benchmarks[support]

    /**
     * Sledeća **teža** prečka koju ovaj zadatak ume, ili `null` ako je nema.
     *
     * Ne pomera se za jedan stepen nego na **sledeću koju zadatak zaista ima**:
     * lestvica je zajednička, ali zadatak sme da preskoči prečke. Geometrija ima
     * samo krajeve, pa je „niže" iz pune podrške odmah bez table.
     */
    fun harderThan(support: Support): Support? =
        supports.filter { it.ordinal > support.ordinal }.minByOrNull { it.ordinal }

    /** Sledeća **lakša** prečka koju zadatak ume, ili `null` ako je nema. */
    fun easierThan(support: Support): Support? =
        supports.filter { it.ordinal < support.ordinal }.maxByOrNull { it.ordinal }

    /**
     * Najbliža prečka koju zadatak ume, kad tražena ne postoji.
     *
     * Pri jednakoj udaljenosti bira se **ona sa više pomoći**. Vežba koja je
     * teža nego što je čovek tražio se ne završi; vežba koja je lakša se bar
     * odradi, a sledeći put se traži niže.
     */
    fun nearestSupport(wanted: Support): Support =
        supports.minByOrNull { candidate ->
            val distance = candidate.ordinal - wanted.ordinal
            if (distance <= 0) -distance * 2 else distance * 2 + 1
        } ?: hardest
}
