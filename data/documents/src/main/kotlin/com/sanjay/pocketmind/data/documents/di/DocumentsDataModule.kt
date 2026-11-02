package com.sanjay.pocketmind.data.documents.di

import com.sanjay.pocketmind.data.documents.PdfBoxTextExtractor
import com.sanjay.pocketmind.data.documents.PdfTextExtractor
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
