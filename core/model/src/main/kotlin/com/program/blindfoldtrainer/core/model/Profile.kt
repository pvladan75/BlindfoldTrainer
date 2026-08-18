package com.program.blindfoldtrainer.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Ko vežba na ovom uređaju.
 *
 * Povod je stvaran: **otac i sin koriste istu aplikaciju**, i svako hoće da vidi
 * svoj napredak, a ne njihov zbir.
 *
 * **Bez lozinke.** Podaci su na uređaju; ko ima uređaj ima i njih, pa lozinka ne
 * bi štitila nego se pretvarala da štiti. Ovde ne treba zaštita nego
 * **razdvajanje napretka** — biranje pri ulasku, kao na televizoru.
 *
 * Profil nije isto što i nalog: nalog služi čuvanju napretka i takmičenju, i
 * kači se na profil po želji. Dete od deset godina najčešće nema nalog, a
 * prebacivanje naloga pred svaku vežbu nije izvodljivo.
 */
data class Profile(
    val id: Long,
    val name: String,
    val createdAtMillis: Long
) {
    init {
        require(name.isNotBlank()) { "profil mora imati ime" }
    }
}

/**
 * Profili i to koji je trenutno u upotrebi.
 *
 * Aktivan profil je svojstvo **uređaja**, ne profila: on kaže ko sad sedi pred
 * telefonom.
 */
interface ProfileRepository {

    val profiles: Flow<List<Profile>>

    /** Profil koji je sad u upotrebi. Uvek postoji bar jedan. */
    val active: Flow<Profile>

    suspend fun create(name: String): Profile

    suspend fun rename(id: Long, name: String)

    suspend fun activate(id: Long)

    /**
     * Briše profil **i celu njegovu istoriju**.
     *
     * Poslednji profil se ne briše: aplikacija bez ijednog profila ne bi imala
     * kome da pripiše sledeću sesiju.
     */
    suspend fun delete(id: Long)
}
