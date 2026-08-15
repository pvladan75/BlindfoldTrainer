package com.program.blindfoldtrainer.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
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
    val finishedAtMillis: Long
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

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class TrainerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "blindfold-trainer"
    }
}

internal fun SessionResult.toEntity(finishedAtMillis: Long) = SessionEntity(
    moduleKey = moduleId.key,
    difficultyName = difficulty.name,
    attempted = attempted,
    solved = solved,
    mistakes = mistakes,
    elapsedMillis = elapsedMillis,
    completed = completed,
    finishedAtMillis = finishedAtMillis
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
            completed = completed
        )
    }.getOrNull()
}
