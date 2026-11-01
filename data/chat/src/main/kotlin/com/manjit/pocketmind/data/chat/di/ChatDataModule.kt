package com.manjit.pocketmind.data.chat.di

import com.manjit.pocketmind.data.chat.ChatRepository
import com.manjit.pocketmind.data.chat.OfflineChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatDataModule {
    @Binds
    abstract fun bindsChatRepository(repository: OfflineChatRepository): ChatRepository
}
