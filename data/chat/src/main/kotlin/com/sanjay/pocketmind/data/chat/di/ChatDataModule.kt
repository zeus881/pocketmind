package com.sanjay.pocketmind.data.chat.di

import com.sanjay.pocketmind.data.chat.ChatRepository
import com.sanjay.pocketmind.data.chat.ConversationSummarizer
import com.sanjay.pocketmind.data.chat.LlmConversationSummarizer
import com.sanjay.pocketmind.data.chat.OfflineChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatDataModule {
    @Binds
    abstract fun bindsChatRepository(repository: OfflineChatRepository): ChatRepository

    @Binds
    abstract fun bindsConversationSummarizer(summarizer: LlmConversationSummarizer): ConversationSummarizer
}
