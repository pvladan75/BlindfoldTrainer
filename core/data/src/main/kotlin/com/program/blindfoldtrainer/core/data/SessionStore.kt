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
    val supportKey: String = "",

    /** Vrsta zadatka; prazno = ne zna se, kao kod svega upisanog ranije. */
    val taskId: String = "",

    /** Da li je red nastao proverom, a ne vežbom. */
    val isCheckup: Boolean = false,

    /**
     * Kome pripada ova sesija.
     *
     * Bez ovoga bi se ocu i sinu istorija slila u jednu, pa bi obojica gledala
     * broj koji ne opisuje nijednog od njih.
     */
    val profileId: Long = DEFAULT_PROFILE_ID
)

/**
 * Jedan korisnik na uređaju.
 *
 * Bez lozinke: podaci su na uređaju, pa lozinka ne bi štitila nego se pretvarala
 * da štiti. Ovde treba razdvajanje napretka, ne zaštita.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMillis: Long
)

/** Profil koji dobija sve što je upisano pre nego što su profili postojali. */
const val DEFAULT_PROFILE_ID = 1L

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY finishedAtMillis")
    fun observeFor(profileId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY finishedAtMillis")
    suspend fun allFor(profileId: Long): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE profileId = :profileId")
    suspend fun clearFor(profileId: Long)
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAtMillis")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAtMillis")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
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
@Database(entities = [SessionEntity::class, ProfileEntity::class], version = 6, exportSchema = false)
abstract class TrainerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun profileDao(): ProfileDao

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

        /** Dodavanje vrste zadatka, da se rezultati ne slivaju preko modula. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE sessions ADD COLUMN taskId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** Razdvajanje provere od vežbe; sve zatečeno je vežba. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE sessions ADD COLUMN isCheckup INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Profili.
         *
         * Zatečena istorija **se ne briše nego pripisuje prvom profilu** — ona
         * je jedino što se u ovoj aplikaciji ne može povratiti. Profil dobija
         * privremeno ime koje korisnik menja.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS profiles (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "createdAtMillis INTEGER NOT NULL)"
                )
                connection.execSQL(
                    "INSERT INTO profiles (id, name, createdAtMillis) " +
                        "VALUES ($DEFAULT_PROFILE_ID, 'Profil 1', 0)"
                )
                connection.execSQL(
                    "ALTER TABLE sessions ADD COLUMN profileId INTEGER NOT NULL " +
                        "DEFAULT $DEFAULT_PROFILE_ID"
                )
            }
        }
    }
}

/** `coordinates:10/8;position_hold:5/4` — po jedan unos za svaku dodirnutu veštinu. */
internal fun Map<Skill, SkillTally>.toStored(): String =
    entries.joinToString(";") { (skill, tally) ->
        "${skill.key}:${tally.attempted}/${tally.solved}/${tally.millis}"
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
        val parts = numbers.split("/")

        // Zapisi bez vremena su iz verzije pre nego što se vreme merilo; čitaju
        // se i dalje, samo bez njega.
        if (parts.size !in 2..3) return@mapNotNull null

        val skill = Skill.entries.find { it.key == key } ?: return@mapNotNull null
        val tally = runCatching {
            SkillTally(
                attempted = parts[0].toInt(),
                solved = parts[1].toInt(),
                millis = parts.getOrNull(2)?.toLong() ?: 0
            )
        }.getOrNull() ?: return@mapNotNull null

        skill to tally
    }.toMap()
}

internal fun SessionResult.toEntity(finishedAtMillis: Long, profileId: Long) = SessionEntity(
    moduleKey = moduleId.key,
    difficultyName = difficulty.name,
    attempted = attempted,
    solved = solved,
    mistakes = mistakes,
    elapsedMillis = elapsedMillis,
    completed = completed,
    finishedAtMillis = finishedAtMillis,
    skillTallies = bySkill.toStored(),
    supportKey = support?.key.orEmpty(),
    taskId = taskId.orEmpty(),
    isCheckup = isCheckup,
    profileId = profileId
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
            support = Support.entries.find { it.key == supportKey },
            finishedAtMillis = finishedAtMillis,
            taskId = taskId.ifBlank { null },
            isCheckup = isCheckup
        )
    }.getOrNull()
}
