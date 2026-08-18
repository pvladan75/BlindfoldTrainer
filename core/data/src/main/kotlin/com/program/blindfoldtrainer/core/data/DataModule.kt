package com.program.blindfoldtrainer.core.data

import android.content.Context
import androidx.room.Room
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.progress.ProgressRepository
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
            .addMigrations(TrainerDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideSessionDao(database: TrainerDatabase): SessionDao = database.sessionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressModule {

    @Binds
    @Singleton
    abstract fun bindProgressRepository(repository: RoomProgressRepository): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository
}
