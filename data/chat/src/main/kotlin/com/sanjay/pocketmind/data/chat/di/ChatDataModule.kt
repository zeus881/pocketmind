package com.sanjay.pocketmind.data.chat.di

import com.sanjay.pocketmind.data.chat.AndroidChatExporter
import com.sanjay.pocketmind.data.chat.ChatExporter
import com.sanjay.pocketmind.data.chat.ChatRepository
import com.sanjay.pocketmind.data.chat.ConversationSummarizer
import com.sanjay.pocketmind.data.chat.FolderRepository
import com.sanjay.pocketmind.data.chat.LlmConversationSummarizer
import com.sanjay.pocketmind.data.chat.OfflineChatRepository
import com.sanjay.pocketmind.data.chat.PersonaRepository
import com.sanjay.pocketmind.data.chat.RoomFolderRepository
import com.sanjay.pocketmind.data.chat.RoomPersonaRepository
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

    @Binds
    abstract fun bindsFolderRepository(repository: RoomFolderRepository): FolderRepository

    @Binds
    abstract fun bindsChatExporter(exporter: AndroidChatExporter): ChatExporter

    @Binds
    abstract fun bindsPersonaRepository(repository: RoomPersonaRepository): PersonaRepository
}
