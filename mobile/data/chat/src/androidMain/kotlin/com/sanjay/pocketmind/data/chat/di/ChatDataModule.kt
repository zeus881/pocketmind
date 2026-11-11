package com.sanjay.pocketmind.data.chat.di

import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.data.chat.AndroidChatExporter
import com.sanjay.pocketmind.data.chat.AndroidChatImageStore
import com.sanjay.pocketmind.data.chat.AndroidUserDataControl
import com.sanjay.pocketmind.data.chat.ChatExporter
import com.sanjay.pocketmind.data.chat.ChatImageStore
import com.sanjay.pocketmind.data.chat.UserDataControl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Chats, folders, personas, photos, export and the model's tools on Android (V3: Koin). The repositories come
 * from the shared [chatCoreModule]; this module adds the parts that need Android (files, PDF drawing,
 * the content resolver).
 */
val chatDataModule = module {
    includes(chatCoreModule)
    factory<ChatExporter> { AndroidChatExporter(androidContext(), get(PmQualifiers.Io)) }
    single<ChatImageStore> { AndroidChatImageStore(androidContext(), get(PmQualifiers.Io)) }
    factory<UserDataControl> {
        AndroidUserDataControl(androidContext(), get(), get(), get(), get(), get(PmQualifiers.Io))
    }
}
