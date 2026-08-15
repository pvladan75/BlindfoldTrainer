package com.program.blindfoldtrainer.core.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindSpeaker(speaker: AndroidSpeaker): Speaker

    @Binds
    @Singleton
    abstract fun bindVoiceInput(voiceInput: VoskVoiceInput): VoiceInput
}
