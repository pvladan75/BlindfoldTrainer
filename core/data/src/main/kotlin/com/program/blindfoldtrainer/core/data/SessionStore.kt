package com.program.blindfoldtrainer.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import kotlinx.coroutines.flow.Flow

/**
 * Jedna završena sesija, onako kako ju je modul prijavio.
 *
 * Čuva se **sirov rezultat, bez poena**. Bodovanje je izvedena vrednost i
 * računa se pri čitanju, pa promena pravila prepravi i staru istoriju umesto da
 * ostavi zamrznute poene iz prethodne verzije.
 *
 * [moduleKey] i [difficultyName] su tekst, a ne redni broj enum-a: dodavanje ili
 * uklanjanje modula ne sme da pomeri značenje već upisanih redova.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleKey: String,
    val difficultyName: String,
    val attempted: Int,
    val solved: Int,
    val mistakes: Int,
    val elapsedMillis: Long,
    val completed: Boolean,
    val finishedAtMillis: Long,

    /**
     * Razlaganje po veštinama, kao tekst: `coordinates:10/8;position_hold:5/4`.
     *
     * Tekst a ne tabela, iz istog razloga iz kog su [moduleKey] i
     * [difficultyName] tekst: nova veština ne sme da pomeri značenje već
     * upisanih redova. Prazno znači **„nije mereno"** — tako izgledaju sve
     * sesije upisane pre nego što su veštine uvedene, i tako se korisniku i
     * kaže.
     */
    val skillTallies: String = "",

    /**
     * Prečka podrške na kojoj je sesija odrađena, kao ključ; prazno = ne zna se.
     *
     * Ne zna se za sve što je upisano pre ove izmene. Takve sesije u profil po
     * veštinama **ne ulaze** — bolje bez podatka nego sa izmišljenim.
     */
    val supportKey: String = ""
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY finishedAtMillis")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY finishedAtMillis")
    suspend fun all(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

/**
 * Verzija 2 je donela [SessionEntity.skillTallies].
 *
 * Migracija samo dodaje kolonu sa praznom vrednošću — **istorija se čuva**.
 * Prazno je ovde pun podatak: stare sesije zaista nisu merene po veštinama i ne
 * mogu se dopuniti unazad, pa se korisniku kaže „nije mereno" umesto nule.
 *
 * `fallbackToDestructiveMigration` se namerno **ne** koristi: napredak je jedino
 * što korisnik u ovoj aplikaciji ima, a on živi u ovoj tabeli.
 */
@Database(entities = [SessionEntity::class], version = 3, exportSchema = false)
abstract class TrainerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "blindfold-trainer"

        /** Dodavanje razlaganja po veštinama; postojeći redovi ostaju „nemereni". */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE sessions ADD COLUMN skillTallies TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** Dodavanje prečke podrške; postojeći redovi ostaju bez nje. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE sessions ADD COLUMN supportKey TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}

/** `coordinates:10/8;position_hold:5/4` — po jedan unos za svaku dodirnutu veštinu. */
internal fun Map<Skill, SkillTally>.toStored(): String =
    entries.joinToString(";") { (skill, tally) ->
        "${skill.key}:${tally.attempted}/${tally.solved}"
    }

/**
 * Nazad iz teksta, uz preskakanje onoga što se ne da pročitati.
 *
 * Nepoznata veština je očekivan slučaj — istorija sme da pominje veštinu koja je
 * u međuvremenu preimenovana ili izbačena. Takav unos otpada, ostatak ostaje;
 * ista logika po kojoj nepoznat modul ne obara ceo napredak.
 */
internal fun String.toSkillTallies(): Map<Skill, SkillTally> {
    if (isBlank()) return emptyMap()

    return split(";").mapNotNull { entry ->
        val (key, numbers) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val (attempted, solved) = numbers.split("/").takeIf { it.size == 2 } ?: return@mapNotNull null

        val skill = Skill.entries.find { it.key == key } ?: return@mapNotNull null
        val tally = runCatching {
            SkillTally(attempted.toInt(), solved.toInt())
        }.getOrNull() ?: return@mapNotNull null

        skill to tally
    }.toMap()
}

internal fun SessionResult.toEntity(finishedAtMillis: Long) = SessionEntity(
    moduleKey = moduleId.key,
    difficultyName = difficulty.name,
    attempted = attempted,
    solved = solved,
    mistakes = mistakes,
    elapsedMillis = elapsedMillis,
    completed = completed,
    finishedAtMillis = finishedAtMillis,
    skillTallies = bySkill.toStored(),
    supportKey = support?.key.orEmpty()
)

/**
 * Red iz baze u rezultat, ili `null` ako se ne da pročitati.
 *
 * Nepoznat modul je očekivan slučaj: korisnik može imati istoriju modula koji je
 * u međuvremenu izbačen. Takav red se preskače, a ostatak napretka ostaje.
 */
internal fun SessionEntity.toResult(): SessionResult? {
    val moduleId = ModuleId.fromKey(moduleKey) ?: return null
    val difficulty = Difficulty.entries.find { it.name == difficultyName } ?: return null

    // SessionResult ima uslove u init-u; oštećen red ne sme da obori ceo napredak.
    return runCatching {
        SessionResult(
            moduleId = moduleId,
            difficulty = difficulty,
            attempted = attempted,
            solved = solved,
            mistakes = mistakes,
            elapsedMillis = elapsedMillis,
            completed = completed,
            bySkill = skillTallies.toSkillTallies(),
            support = Support.entries.find { it.key == supportKey }
        )
    }.getOrNull()
}
