package com.sanjay.pocketmind.data.documents.di

import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.data.documents.Summarizer
import com.sanjay.pocketmind.data.documents.Translator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The shared (V3, shared:ai) summarizer and translator, created for Android's Hilt graph. */
@Module
@InstallIn(SingletonComponent::class)
internal object DocumentTasksModule {
    @Provides
    fun providesSummarizer(task: LlmTask): Summarizer = Summarizer(task)

    @Provides
    fun providesTranslator(task: LlmTask): Translator = Translator(task)
}
