package com.program.blindfoldtrainer.core.data

import android.content.Context
import androidx.room.Room
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.progress.ProgressRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.program.blindfoldtrainer.core.model.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrainerDatabase =
        Room.databaseBuilder(context, TrainerDatabase::class.java, TrainerDatabase.NAME)
            .addMigrations(
                TrainerDatabase.MIGRATION_1_2,
                TrainerDatabase.MIGRATION_2_3,
                TrainerDatabase.MIGRATION_3_4,
                TrainerDatabase.MIGRATION_4_5,
                TrainerDatabase.MIGRATION_5_6,
                TrainerDatabase.MIGRATION_6_7
            )
            .build()

    @Provides
    fun provideSessionDao(database: TrainerDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideProfileDao(database: TrainerDatabase): ProfileDao = database.profileDao()

    /**
     * Isti DataStore koji nosi podešavanja nosi i **aktivan profil**.
     *
     * Aktivan profil je svojstvo uređaja, ne profila: kaže ko sad sedi pred
     * telefonom, pa mu je mesto uz ostala podešavanja uređaja.
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(repository: RoomProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(repository: RoomProgressRepository): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository
}
