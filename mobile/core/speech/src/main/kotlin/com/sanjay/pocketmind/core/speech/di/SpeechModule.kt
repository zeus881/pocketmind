package com.sanjay.pocketmind.core.speech.di

import com.sanjay.pocketmind.core.speech.AndroidSpeechInput
import com.sanjay.pocketmind.core.speech.AndroidSpeechOutput
import com.sanjay.pocketmind.core.speech.SpeechInput
import com.sanjay.pocketmind.core.speech.SpeechOutput
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** On-device voice in and out: one recognizer and one text-to-speech engine for the whole app. */
val speechModule = module {
    single<SpeechInput> { AndroidSpeechInput(androidContext()) }
    single<SpeechOutput> { AndroidSpeechOutput(androidContext()) }
}
