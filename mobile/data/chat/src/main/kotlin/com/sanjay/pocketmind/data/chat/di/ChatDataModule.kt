package com.sanjay.pocketmind.data.chat.di

import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.data.chat.AndroidChatExporter
import com.sanjay.pocketmind.data.chat.AndroidChatImageStore
import com.sanjay.pocketmind.data.chat.AndroidUserDataControl
import com.sanjay.pocketmind.data.chat.AnswerEvents
import com.sanjay.pocketmind.data.chat.ChatExporter
import com.sanjay.pocketmind.data.chat.ChatImageStore
import com.sanjay.pocketmind.data.chat.ChatModel
import com.sanjay.pocketmind.data.chat.ChatPhotos
import com.sanjay.pocketmind.data.chat.ChatRepository
import com.sanjay.pocketmind.data.chat.ChatTools
import com.sanjay.pocketmind.data.chat.ConversationSummarizer
import com.sanjay.pocketmind.data.chat.FolderRepository
import com.sanjay.pocketmind.data.chat.LlmConversationSummarizer
import com.sanjay.pocketmind.data.chat.OfflineChatRepository
import com.sanjay.pocketmind.data.chat.PersonaRepository
import com.sanjay.pocketmind.data.chat.RoomFolderRepository
import com.sanjay.pocketmind.data.chat.RoomPersonaRepository
import com.sanjay.pocketmind.data.chat.UserDataControl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

/** Chats, folders, personas, photos, export and the model's tools (V3: Koin; same scopes as v2's Hilt module). */
val chatDataModule = module {
    // ONE repository: the session bookkeeping must be shared by every screen. The chat screen sees it through
    // three small interfaces (chats, the model, photos).
    single {
        OfflineChatRepository(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    } binds arrayOf(ChatRepository::class, ChatModel::class, ChatPhotos::class)
    factory<ConversationSummarizer> { LlmConversationSummarizer(get()) }
    factory<FolderRepository> { RoomFolderRepository(get(), get()) }
    factory<PersonaRepository> { RoomPersonaRepository(get(), get()) }
    factory<ChatExporter> { AndroidChatExporter(androidContext(), get(PmQualifiers.Io)) }
    single<ChatImageStore> { AndroidChatImageStore(androidContext(), get(PmQualifiers.Io)) }
    factory<UserDataControl> {
        AndroidUserDataControl(androidContext(), get(), get(), get(), get(), get(PmQualifiers.Io))
    }
    single { ChatTools(get(), get()) }
    single { AnswerEvents() }
}
