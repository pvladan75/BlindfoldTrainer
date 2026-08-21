package com.program.blindfoldtrainer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čuvar za **tekst koji korisnik vidi**.
 *
 * Kod ovde ima preko četiri stotine testova; rečenice nisu imale nijedan. A one
 * umeju da otkažu tiho: Android **briše neescape-ovan ASCII navodnik** iz
 * resursa, pa je „g1 f3" na ekranu ostajalo otvoreno — `Čuješ „g1 f3 i vidiš`.
 * Bilo je pogođeno dvadeset mesta i nijedno se nije videlo dok se ne pročita
 * naglas.
 *
 * Isto važi za apostrof. Oba znaka su i inače pogrešna u srpskom tekstu: navodnici
 * su „ i “, pa test istovremeno čuva i tipografiju i to da tekst uopšte stigne do
 * ekrana.
 */
class StringsTest {

    private val strings = File("src/main/res/values/strings.xml").readText()

    /** Ime resursa i njegov tekst; `<string-array>` se ovde ne dira. */
    private val entries: List<Pair<String, String>> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(strings)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    @Test
    fun `spisak se uopste procitao`() {
        assertTrue("nijedan string nije nađen — regularni izraz ne valja", entries.size > 50)
    }

    /**
     * Android **guta** neescape-ovan navodnik. Rečenica se prevede, prođe build i
     * pojavi se na ekranu bez njega — pa se greška vidi tek kad je neko pročita.
     */
    @Test
    fun `nijedan tekst ne sadrzi ASCII navodnik`() {
        val bad = entries.filter { (_, body) -> '"' in body }

        assertTrue(
            "ASCII navodnik nestaje sa ekrana; koristi „ i “ — ${bad.map { it.first }}",
            bad.isEmpty()
        )
    }

    /** Isto pravilo, isti ishod: neescape-ovan apostrof se ne prikazuje. */
    @Test
    fun `nijedan tekst ne sadrzi neescape-ovan apostrof`() {
        val bad = entries.filter { (_, body) ->
            body.withIndex().any { (i, ch) -> ch == '\'' && (i == 0 || body[i - 1] != '\\') }
        }

        assertTrue(
            "apostrof mora da se escape-uje sa \\' — ${bad.map { it.first }}",
            bad.isEmpty()
        )
    }

    /**
     * Otvoren navodnik bez zatvorenog je znak da je zatvoreni upravo pojeden —
     * ista greška, uhvaćena i sa druge strane.
     */
    @Test
    fun `navodnici dolaze u parovima`() {
        val bad = entries.filter { (_, body) -> body.count { it == '„' } != body.count { it == '“' } }

        assertTrue(
            "otvoreni i zatvoreni navodnik se ne poklapaju — ${bad.map { it.first }}",
            bad.isEmpty()
        )
    }
}
