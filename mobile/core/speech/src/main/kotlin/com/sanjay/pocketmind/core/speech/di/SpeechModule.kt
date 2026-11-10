package com.sanjay.pocketmind.core.speech.di

import com.sanjay.pocketmind.core.speech.AndroidSpeechInput
import com.sanjay.pocketmind.core.speech.AndroidSpeechOutput
import com.sanjay.pocketmind.core.speech.SpeechInput
import com.sanjay.pocketmind.core.speech.SpeechOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpeechModule {
    @Binds
    abstract fun bindsSpeechInput(input: AndroidSpeechInput): SpeechInput

    @Binds
    abstract fun bindsSpeechOutput(output: AndroidSpeechOutput): SpeechOutput
}
