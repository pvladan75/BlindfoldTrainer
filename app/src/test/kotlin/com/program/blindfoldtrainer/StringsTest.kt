package com.program.blindfoldtrainer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čuvar za **tekst koji korisnik vidi**.
 *
 * Kod ovde ima preko tri stotine testova; rečenice nisu imale nijedan. A one
 * umeju da otkažu tiho: Android **briše neescape-ovan ASCII navodnik i
 * apostrof** iz resursa, pa „g1 f3" na ekranu ostaje otvoreno, a „don't" postane
 * „dont". Bilo je pogođeno dvadeset mesta i nijedno se nije videlo dok se ne
 * pročita naglas.
 *
 * Tekst je od 18. oktobra 2026. na engleskom, pa je pravilo englesko: navodnici
 * su “ i ”, apostrof je ’. Sirovi ASCII znak se ne dozvoljava ni escape-ovan —
 * `\'` prolazi build, ali tipografski je pogrešan i lako se zaboravi, a ovako je
 * jedno pravilo bez izuzetka.
 *
 * Proverava se i tekst modula u `feature`, iz istog razloga i istim pravilom.
 */
class StringsTest {

    private val files: List<File> = buildList {
        add(File("src/main/res/values/strings.xml"))
        File("../feature").listFiles()
            .orEmpty()
            .map { File(it, "src/main/res/values/strings.xml") }
            .filter { it.isFile }
            .sortedBy { it.path }
            .forEach { add(it) }
    }

    /** Ime resursa i njegov tekst; `<string-array>` se ovde ne dira. */
    private val entries: List<Pair<String, String>> = files.flatMap { file ->
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

    @Test
    fun `spisak se uopste procitao`() {
        assertTrue("nijedan string nije nađen — regularni izraz ne valja", entries.size > 50)
        assertTrue("očekivano je devet modula uz app — nađeno ${files.size} fajlova", files.size == 10)
    }

    /**
     * Android **guta** neescape-ovan navodnik. Rečenica se prevede, prođe build i
     * pojavi se na ekranu bez njega — pa se greška vidi tek kad je neko pročita.
     */
    @Test
    fun `nijedan tekst ne sadrzi ASCII navodnik`() {
        val bad = entries.filter { (_, body) -> '"' in body }

        assertTrue(
            "ASCII navodnik nestaje sa ekrana; koristi “ i ” — ${bad.map { it.first }}",
            bad.isEmpty()
        )
    }

    /**
     * Isto pravilo za apostrof, i **bez izuzetka za escape**: engleski tekst je
     * pun kontrakcija, a „don't" koje tiho postane „dont" niko ne primeti dok ga
     * ne pročita. Kovrdžavi ’ Android ne dira.
     */
    @Test
    fun `nijedan tekst ne sadrzi ASCII apostrof`() {
        val bad = entries.filter { (_, body) -> '\'' in body }

        assertTrue(
            "ASCII apostrof nestaje sa ekrana; koristi ’ — ${bad.map { it.first }}",
            bad.isEmpty()
        )
    }

    /**
     * Otvoren navodnik bez zatvorenog je znak da je zatvoreni upravo pojeden —
     * ista greška, uhvaćena i sa druge strane.
     */
    @Test
    fun `navodnici dolaze u parovima`() {
        val bad = entries.filter { (_, body) -> body.count { it == '“' } != body.count { it == '”' } }

        assertTrue(
            "otvoreni i zatvoreni navodnik se ne poklapaju — ${bad.map { it.first }}",
            bad.isEmpty()
        )
    }

    /** Srpski navodnici („) su ostatak prevoda; u engleskom tekstu nemaju šta da traže. */
    @Test
    fun `nema srpskih navodnika`() {
        val bad = entries.filter { (_, body) -> '„' in body }

        assertTrue("srpski otvoreni navodnik u engleskom tekstu — ${bad.map { it.first }}", bad.isEmpty())
    }
}
