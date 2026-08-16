package com.program.blindfoldtrainer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Arhiva sa jednim omotačkim folderom, kao što Vosk isporučuje. */
    private fun archiveOf(vararg entries: String): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("$ROOT/"))
            zip.closeEntry()
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry("$ROOT/$name"))
                zip.write("sadržaj".toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun completeArchive() = archiveOf(
        "am/final.mdl",
        "conf/model.conf",
        "conf/mfcc.conf",
        "graph/HCLr.fst",
        "graph/Gr.fst",
        "ivector/final.dubm",
        "README"
    )

    @Test
    fun `omotacki folder se skida`() {
        val target = temporaryFolder.newFolder("model")
        val count = ModelArchive.unpack(completeArchive(), target)

        assertEquals(7, count)
        assertTrue("am/final.mdl nije na svom mestu", File(target, "am/final.mdl").isFile)
        assertFalse("omotač je ostao", File(target, ROOT).exists())
    }

    @Test
    fun `potpun model se prepoznaje`() {
        val target = temporaryFolder.newFolder("model")
        ModelArchive.unpack(completeArchive(), target)

        assertTrue(ModelArchive.isComplete(target))
    }

    @Test
    fun `model kojem fali fajl nije potpun`() {
        val target = temporaryFolder.newFolder("model")
        // Sve osim akustičkog modela — folder postoji i deluje kao model.
        ModelArchive.unpack(
            archiveOf("conf/model.conf", "conf/mfcc.conf", "graph/HCLr.fst"),
            target
        )

        assertFalse(ModelArchive.isComplete(target))
    }

    @Test
    fun `prazan folder nije model`() {
        assertFalse(ModelArchive.isComplete(temporaryFolder.newFolder("prazno")))
    }

    @Test
    fun `unos koji vodi van foldera se odbija`() {
        val target = temporaryFolder.newFolder("model")

        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("$ROOT/../../ukradeno.txt"))
            zip.write("zlo".toByteArray())
            zip.closeEntry()
        }

        val failure = runCatching {
            ModelArchive.unpack(ByteArrayInputStream(bytes.toByteArray()), target)
        }.exceptionOrNull()

        assertTrue("raspakivanje je prošlo: $failure", failure is IllegalArgumentException)
        assertFalse(File(target.parentFile, "ukradeno.txt").exists())
    }

    private companion object {
        const val ROOT = "vosk-model-small-en-us-0.15"
    }
}
