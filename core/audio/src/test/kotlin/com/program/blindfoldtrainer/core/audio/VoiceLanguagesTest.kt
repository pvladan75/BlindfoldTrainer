package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguagesTest {

    @Test
    fun `svaki jezik ima svoj model`() {
        VoiceLanguage.entries.forEach { language ->
            val spec = VoiceLanguages.specFor(language)

            assertTrue("${language.name}: nema arhive", spec.archiveName.endsWith(".zip"))
            assertTrue("${language.name}: čudna veličina", spec.downloadMegabytes in 10..200)
            assertTrue(
                "${language.name}: adresa ne vodi na Vosk",
                VoiceLanguages.urlFor(language).startsWith(VoiceLanguages.BASE_URL)
            )
        }
    }

    @Test
    fun `svaki jezik pokriva svih 64 polja`() {
        VoiceLanguage.entries.forEach { language ->
            val words = VoiceLanguages.specFor(language).words

            words.files.forEach { (fileWord, file) ->
                words.ranks.forEach { (rankWord, rank) ->
                    val expected = Square.fromAlgebraic("$file$rank")
                    assertEquals(
                        "${language.name}: \"$fileWord $rankWord\"",
                        SpokenInput.Full(requireNotNull(expected)),
                        parseSpokenInput("$fileWord $rankWord", words)
                    )
                }
            }
        }
    }

    @Test
    fun `rec se ne ponavlja unutar jezika`() {
        VoiceLanguage.entries.forEach { language ->
            val words = VoiceLanguages.specFor(language).words
            val all = words.allWords

            assertEquals(
                "${language.name}: ista reč znači dve stvari",
                all.size,
                all.toSet().size
            )
        }
    }

    @Test
    fun `recnik ima tacno sesnaest reci`() {
        VoiceLanguage.entries.forEach { language ->
            assertEquals(
                language.name,
                16,
                VoiceLanguages.specFor(language).words.allWords.size
            )
        }
    }

    @Test
    fun `fonetska abeceda radi na svakom jeziku`() {
        // Fonetske reči ne zavise od jezika i zato prolaze uz bilo koju tabelu.
        VoiceLanguage.entries.forEach { language ->
            val words = VoiceLanguages.specFor(language).words
            val rankWord = words.ranks.entries.first { it.value == '3' }.key

            assertEquals(
                language.name,
                SpokenInput.Full(requireNotNull(Square.fromAlgebraic("d3"))),
                parseSpokenInput("delta $rankWord", words)
            )
        }
    }

    @Test
    fun `samo engleski je proveren na uredjaju`() {
        // Reči za ostale jezike su upisane po pravopisu, ne po sluhu. Kad se koji
        // proveri, oznaka se menja ovde i test prati stvarnost.
        val verified = VoiceLanguage.entries.filter { VoiceLanguages.specFor(it).isVerified }

        assertEquals(listOf(VoiceLanguage.ENGLISH), verified)
    }
}
