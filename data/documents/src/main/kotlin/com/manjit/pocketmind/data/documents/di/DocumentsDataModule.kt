package com.manjit.pocketmind.data.documents.di

import com.manjit.pocketmind.data.documents.PdfBoxTextExtractor
import com.manjit.pocketmind.data.documents.PdfTextExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DocumentsDataModule {
    @Binds
    abstract fun bindsPdfTextExtractor(extractor: PdfBoxTextExtractor): PdfTextExtractor
}
