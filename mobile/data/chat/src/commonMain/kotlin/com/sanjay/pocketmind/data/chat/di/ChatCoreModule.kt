package com.sanjay.pocketmind.data.chat.di

import com.sanjay.pocketmind.data.chat.AnswerEvents
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
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The platform-free half of the chat data layer (V3): repositories over the shared Room database and the
 * on-device model. Android adds photos, export and "your data" in `chatDataModule`; iOS will add its own
 * versions of those (phase 2e). Same scopes as v2's Hilt module.
 */
val chatCoreModule = module {
    // ONE repository: the session bookkeeping must be shared by every screen. The chat screen sees it through
    // three small interfaces (chats, the model, photos).
    single {
        OfflineChatRepository(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    } binds arrayOf(ChatRepository::class, ChatModel::class, ChatPhotos::class)
    factory<ConversationSummarizer> { LlmConversationSummarizer(get()) }
    factory<FolderRepository> { RoomFolderRepository(get(), get()) }
    factory<PersonaRepository> { RoomPersonaRepository(get(), get()) }
    single { ChatTools(get(), get()) }
    single { AnswerEvents() }
}
