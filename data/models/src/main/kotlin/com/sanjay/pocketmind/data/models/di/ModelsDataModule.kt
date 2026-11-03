package com.sanjay.pocketmind.data.models.di

import android.content.Context
import androidx.work.WorkManager
import com.sanjay.pocketmind.core.llm.EmbeddingModelSource
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.data.models.AndroidDeviceInfo
import com.sanjay.pocketmind.data.models.CatalogEmbeddingModelSource
import com.sanjay.pocketmind.data.models.DeviceInfo
import com.sanjay.pocketmind.data.models.ModelRepository
import com.sanjay.pocketmind.data.models.SettingsInferenceSource
import com.sanjay.pocketmind.data.models.WorkManagerModelRepository
import com.sanjay.pocketmind.data.models.download.HostAllowlist
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModelsDataModule {
    @Binds
    abstract fun bindsDeviceInfo(info: AndroidDeviceInfo): DeviceInfo

    @Binds
    abstract fun bindsEmbeddingModelSource(source: CatalogEmbeddingModelSource): EmbeddingModelSource

    companion object {
        /** The app's ONLY HTTP client. HostAllowlist blocks every host except Hugging Face. */
        @Provides
        @Singleton
        fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(HostAllowlist)
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        @Provides
        fun providesWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

        private const val CONNECT_TIMEOUT_S = 30L
        private const val READ_TIMEOUT_S = 60L
    }
}

/**
 * Which models exist and which one answers. Public and separate so end-to-end tests can replace exactly
 * this (with an instant "download") while the rest of the app's wiring stays real.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModelSelectionModule {
    @Binds
    internal abstract fun bindsModelRepository(repository: WorkManagerModelRepository): ModelRepository

    @Binds
    internal abstract fun bindsInferenceSettingsSource(source: SettingsInferenceSource): InferenceSettingsSource
}
