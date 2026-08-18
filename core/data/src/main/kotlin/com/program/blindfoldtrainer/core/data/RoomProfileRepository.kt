package com.program.blindfoldtrainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.program.blindfoldtrainer.core.model.Profile
import com.program.blindfoldtrainer.core.model.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profili u Room-u, a **koji je aktivan** u DataStore-u.
 *
 * Podela nije proizvoljna: spisak profila je podatak aplikacije, a to ko sad
 * sedi pred telefonom je svojstvo **uređaja**. Zato aktivan profil ne stoji u
 * tabeli profila nego uz ostala podešavanja uređaja.
 */
@Singleton
class RoomProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val sessions: SessionDao,
    private val store: DataStore<Preferences>
) : ProfileRepository {

    override val profiles: Flow<List<Profile>> =
        dao.observeAll().map { rows -> rows.map { it.toProfile() } }

    /**
     * Aktivan profil, uvek postojeći.
     *
     * Ako upamćeni profil više ne postoji — obrisan je — uzima se prvi sa
     * spiska umesto da aplikacija ostane bez profila. Prazan spisak se popunjava
     * pri prvom čitanju, jer sesija mora imati kome da se pripiše.
     */
    override val active: Flow<Profile> = combine(
        dao.observeAll(),
        store.data.map { it[ACTIVE_PROFILE] }
    ) { rows, activeId ->
        val existing = rows.map { it.toProfile() }
        existing.find { it.id == activeId } ?: existing.firstOrNull() ?: ensureDefault()
    }

    override suspend fun create(name: String): Profile {
        val id = dao.insert(
            ProfileEntity(name = name.trim(), createdAtMillis = System.currentTimeMillis())
        )
        return Profile(id = id, name = name.trim(), createdAtMillis = System.currentTimeMillis())
    }

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    override suspend fun activate(id: Long) {
        store.edit { preferences -> preferences[ACTIVE_PROFILE] = id }
    }

    /**
     * Briše profil i **celu njegovu istoriju**.
     *
     * Poslednji se ne briše: aplikacija bez profila ne bi imala kome da pripiše
     * sledeću sesiju. Istorija se briše izričito, a ne kroz strani ključ, da bi
     * se videlo da je to namera a ne posledica.
     */
    override suspend fun delete(id: Long) {
        if (dao.all().size <= 1) return

        sessions.clearFor(id)
        dao.delete(id)
    }

    /**
     * Prvi profil na praznoj bazi.
     *
     * Traži se **bilo koji** postojeći, ne baš prvi po broju: nadogradnja ga
     * napravi u migraciji, sveža instalacija ovde. Bez te provere bi dva brza
     * čitanja praznog spiska napravila dva profila.
     */
    private suspend fun ensureDefault(): Profile = mutex.withLock {
        dao.all().firstOrNull()?.let { return it.toProfile() }

        val now = System.currentTimeMillis()
        val id = dao.insert(ProfileEntity(name = FIRST_PROFILE_NAME, createdAtMillis = now))
        Profile(id = id, name = FIRST_PROFILE_NAME, createdAtMillis = now)
    }

    private val mutex = Mutex()

    private companion object {
        val ACTIVE_PROFILE = longPreferencesKey("active_profile")
        const val FIRST_PROFILE_NAME = "Profil 1"
    }
}

private fun ProfileEntity.toProfile() = Profile(
    id = id,
    name = name,
    createdAtMillis = createdAtMillis
)
