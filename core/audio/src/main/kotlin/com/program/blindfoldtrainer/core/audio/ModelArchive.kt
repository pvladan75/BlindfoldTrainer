package com.program.blindfoldtrainer.core.audio

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Raspakivanje preuzetog Vosk modela.
 *
 * Izdvojeno iz preuzimanja da bi moglo da se testira bez mreže i bez uređaja —
 * upravo su ovakvi delovi (putanje, prekinuto raspakivanje) ono što kasnije puca
 * na telefonu.
 */
object ModelArchive {

    /**
     * Raspakuje arhivu u [target], **bez omotačkog foldera**. Vosk arhive imaju
     * jedan folder na vrhu (`vosk-model-small-en-us-0.15/…`), a `Model()` očekuje
     * putanju do onoga što je unutra.
     *
     * Vraća broj raspakovanih fajlova.
     */
    fun unpack(source: InputStream, target: File): Int {
        val root = target.canonicalFile
        root.mkdirs()

        var files = 0
        ZipInputStream(source).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relative.isNotEmpty() && !entry.isDirectory) {
                    val destination = File(root, relative).canonicalFile

                    // Unos tipa "../../nesto" bi inače pisao van foldera.
                    require(destination.path.startsWith(root.path + File.separator)) {
                        "Unos vodi van foldera: ${entry.name}"
                    }

                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { zip.copyTo(it) }
                    files++
                }
                entry = zip.nextEntry
            }
        }
        return files
    }

    /**
     * Da li folder sadrži upotrebljiv model. Prisustvo foldera nije dovoljno —
     * prekinuto preuzimanje ostavlja folder koji izgleda kao model, a nije.
     */
    fun isComplete(directory: File): Boolean =
        REQUIRED_ENTRIES.all { File(directory, it).isFile }

    /** Fajlovi bez kojih Vosk model ne može da se učita. */
    private val REQUIRED_ENTRIES = listOf(
        "am/final.mdl",
        "conf/model.conf",
        "conf/mfcc.conf",
        "graph/HCLr.fst",
        "graph/Gr.fst",
        "ivector/final.dubm"
    )
}
