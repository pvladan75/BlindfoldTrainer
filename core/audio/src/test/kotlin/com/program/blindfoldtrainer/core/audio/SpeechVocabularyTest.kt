package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drži rečnik, manifest, katanac renderera i same isečke u jednom koraku.
 *
 * Isečak se ne može proveriti u testu — zvuk sluša čovek — ali sve oko njega
 * može: da je manifest baš ono što rečnik kaže, da za svaki isečak postoji fajl
 * u traženom formatu, da je snimljen po receptu koji manifest nosi (katanac),
 * i da nijedna rečenica ne traži isečak kog nema niti rečnik nosi isečak koji
 * niko ne izgovara. Tekst izmenjen u rečniku bez ponovnog snimanja je zato
 * crven test, a ne isečak koji kaže staru reč.
 */
class SpeechVocabularyTest {

    private val assets = File("src/main/assets/speech")
    private val manifest = File(assets, "manifest.json")
    private val lock = File(assets, "rendered.json")

    /**
     * Manifest je **ispis rečnika**. Kad se razlikuju, očekivani se upiše u
     * `build/`, da se prekopira i snimi — ne da se ručno dopisuje.
     */
    @Test
    fun `manifest je ispis recnika`() {
        val expected = SpeechVocabulary.manifestJson()
        val actual = if (manifest.isFile) manifest.readText() else ""
        if (expected != actual) {
            val out = File("build/speech-manifest/manifest.json")
            out.parentFile.mkdirs()
            out.writeText(expected)
            throw AssertionError(
                "manifest.json ne odgovara rečniku. Očekivani je upisan u ${out.path}; " +
                    "prekopiraj ga u ${manifest.path} i snimi isečke: " +
                    "node tools/speech_clips/render.js --manifest <putanja> (iz Mislishe)."
            )
        }
    }

    @Test
    fun `svaki isecak ima fajl u trazenom formatu`() {
        val missing = mutableListOf<String>()
        val wrong = mutableListOf<String>()
        for (token in SpeechVocabulary.all) {
            val file = File(assets, "${token.id}.wav")
            if (!file.isFile) {
                missing += token.id
                continue
            }
            val clip = WavPcm.read(file.readBytes())
            val f = clip.format
            if (f.sampleRate != SpeechVocabulary.SAMPLE_RATE ||
                f.bitsPerSample != SpeechVocabulary.BITS_PER_SAMPLE ||
                f.channels != SpeechVocabulary.CHANNELS ||
                clip.millis < 40
            ) {
                wrong += "${token.id} (${f.sampleRate} Hz, ${f.bitsPerSample} bit, ${f.channels} ch, ${clip.millis} ms)"
            }
        }
        assertTrue("isečci bez fajla: $missing", missing.isEmpty())
        assertTrue("isečci pogrešnog oblika: $wrong", wrong.isEmpty())
    }

    /** Katanac koji render ostavi mora da kaže da je svaki isečak snimljen baš po ovom receptu. */
    @Test
    fun `katanac se slaze sa receptom`() {
        assertTrue("nema ${lock.path} — render nije puštan", lock.isFile)
        val text = lock.readText()
        val stale = mutableListOf<String>()
        for (token in SpeechVocabulary.all) {
            val entry = Regex("\"${Regex.escape(token.id)}\":\\s*\\{([^}]*)\\}").find(text)?.groupValues?.get(1)
            if (entry == null) {
                stale += "${token.id} (nema u katancu)"
                continue
            }
            val kind = Regex("\"kind\":\\s*\"([^\"]*)\"").find(entry)?.groupValues?.get(1)
            val carrier = Regex("\"carrier\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(entry)?.groupValues?.get(1)?.replace("\\\"", "\"")
            val from = Regex("\"wordFrom\":\\s*(null|\\d+)").find(entry)?.groupValues?.get(1)
            val to = Regex("\"wordTo\":\\s*(null|\\d+)").find(entry)?.groupValues?.get(1)
            val voice = Regex("\"voice\":\\s*\"([^\"]*)\"").find(entry)?.groupValues?.get(1)
            val expectedKind = if (token.isPhrase) "phrase" else "cut"
            if (kind != expectedKind || carrier != token.carrier || voice != SpeechVocabulary.VOICE ||
                from != (token.wordFrom?.toString() ?: "null") || to != (token.wordTo?.toString() ?: "null")
            ) {
                stale += token.id
            }
        }
        assertTrue("isečci snimljeni po starom receptu — pusti render: $stale", stale.isEmpty())
    }

    /** Nijedan fajl bez isečka u rečniku: render briše višak, a ručno dodat bi lagao. */
    @Test
    fun `nema fajla bez iseka u recniku`() {
        val ids = SpeechVocabulary.all.map { it.id }.toSet()
        val orphans = assets.listFiles().orEmpty()
            .filter { it.name.endsWith(".wav") }
            .map { it.name.removeSuffix(".wav") }
            .filterNot { it in ids }
        assertTrue("fajlovi bez isečka u rečniku: $orphans", orphans.isEmpty())
    }

    /**
     * Svaki isečak koji rečenice traže postoji, i svaki isečak koji rečnik nosi
     * neka rečenica traži. Rečenice se prolaze refleksijom preko **svih** članova
     * sučelja sa svim vrednostima parametara koje modul može da pošalje.
     */
    @Test
    fun `recenice i recnik traze isto`() {
        val used = mutableSetOf<String>()
        val voice = voiceFor(com.program.blindfoldtrainer.core.model.Language.ENGLISH)

        for (member in SpeechPhrases::class.java.methods) {
            for (arguments in argumentSweep(member.parameterTypes, member.name)) {
                val line = try {
                    member.invoke(voice, *arguments) as SpokenLine
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw AssertionError("${member.name}(${arguments.joinToString()}) threw ${e.targetException}", e.targetException)
                }
                used += line.clipIds
            }
        }
        Square.ALL.forEach { square ->
            used += SpokenLine(listOf(SpeechVocabulary.square(square))).clipIds
            used += SpokenLine(SpeechVocabulary.squarePhonetic(square)).clipIds
        }
        used += SpokenLine(SpeechVocabulary.move(Move(Square.ALL[0], Square.ALL[1]))).clipIds
        PieceType.entries.forEach { used += SpeechVocabulary.piece(it).id }
        val board = Board.of(
            Square.ALL.take(12).mapIndexed { i, sq ->
                sq to Piece(PieceType.entries[i % 6], if (i < 6) Color.WHITE else Color.BLACK)
            }.toMap()
        )
        used += SpokenLine(SpeechVocabulary.board(board)).clipIds

        val declared = SpeechVocabulary.all.map { it.id }.toSet()
        assertEquals("rečenice traže isečke kojih nema u rečniku", emptySet<String>(), used - declared)
        assertEquals("rečnik nosi isečke koje niko ne izgovara", emptySet<String>(), declared - used)
    }

    /** Imena isečaka su jedinstvena i ispravna kao imena fajlova — rečnik to već traži pri upisu. */
    @Test
    fun `recnik se uopste sastavio`() {
        val ids = SpeechVocabulary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size > 300)
    }

    private fun argumentSweep(types: Array<Class<*>>, name: String): List<Array<Any>> {
        if (types.isEmpty()) return listOf(emptyArray())
        // `Square` je value class, pa u Javi stiže kao `int` i ime metode dobija
        // sufiks; ove dve ga primaju na drugom mestu. Polja pokriva zasebna petlja.
        val base = name.substringBefore('-')
        val squareAt = if (base == "whoAttacks" || base == "notThereIsOn") 1 else -1
        // Red je 1–8; sve ostalo što prima broj prima ga kao količinu.
        val ints: List<Any> =
            if (base == "reachWhichOnRank") (1..8).toList()
            else (0..100).toList() + (101..999 step 100).toList() + (1000..9000 step 1000).toList() + listOf(250, 999, 1005, 1205, 9999)
        val domains: List<List<Any>> = types.mapIndexed { index, type ->
            when {
                index == squareAt -> listOf(0)
                type == Int::class.javaPrimitiveType -> ints
                type == Boolean::class.javaPrimitiveType -> listOf(true, false)
                type == Char::class.javaPrimitiveType -> ('a'..'h').toList()
                type == Piece::class.java -> PieceType.entries.flatMap { t -> Color.entries.map { Piece(t, it) } }
                type == List::class.java -> listOf(Square.ALL.take(3))
                type.isEnum -> requireNotNull(type.enumConstants).toList()
                else -> error("no domain for $type in $name")
            }
        }
        // Dekartov proizvod bi za tri Int-a bio prevelik; isti broj na svim mestima
        // pokriva iste isečke, jer svaki Int bira isečak nezavisno od ostalih.
        val intSlots = types.indices.filter { it != squareAt && types[it] == Int::class.javaPrimitiveType }
        if (intSlots.size > 1) {
            require(intSlots.size == types.size) { "unsupported mix of parameters in $name" }
            return ints.map { n -> Array(types.size) { n } }
        }
        return domains.fold(listOf(emptyList<Any>())) { acc, domain ->
            acc.flatMap { prefix -> domain.map { prefix + it } }
        }.map { it.toTypedArray() }
    }
}
