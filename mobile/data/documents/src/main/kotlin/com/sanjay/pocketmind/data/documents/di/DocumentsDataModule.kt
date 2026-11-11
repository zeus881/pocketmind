package com.sanjay.pocketmind.data.documents.di

import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.data.documents.PdfBoxTextExtractor
import com.sanjay.pocketmind.data.documents.PdfTextExtractor
import com.sanjay.pocketmind.data.documents.Summarizer
import com.sanjay.pocketmind.data.documents.Translator
import com.sanjay.pocketmind.data.documents.rag.DocumentRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** PDF reading (Android: PdfBox), the document index (RAG), and the shared summarizer and translator. */
val documentsDataModule = module {
    single<PdfTextExtractor> { PdfBoxTextExtractor(androidContext(), get(PmQualifiers.Io), get()) }
    single { DocumentRepository(get(), get(), get(), get(), get(), get(), get(PmQualifiers.Cpu)) }
    factory { Summarizer(get()) }
    factory { Translator(get()) }
}
