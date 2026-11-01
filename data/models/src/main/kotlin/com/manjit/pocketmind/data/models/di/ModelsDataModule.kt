package com.manjit.pocketmind.data.models.di

import android.content.Context
import androidx.work.WorkManager
import com.manjit.pocketmind.core.llm.InferenceSettingsSource
import com.manjit.pocketmind.data.models.AndroidDeviceInfo
import com.manjit.pocketmind.data.models.DeviceInfo
import com.manjit.pocketmind.data.models.ModelRepository
import com.manjit.pocketmind.data.models.SettingsInferenceSource
import com.manjit.pocketmind.data.models.WorkManagerModelRepository
import com.manjit.pocketmind.data.models.download.HostAllowlist
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
    abstract fun bindsModelRepository(repository: WorkManagerModelRepository): ModelRepository

    @Binds
    abstract fun bindsDeviceInfo(info: AndroidDeviceInfo): DeviceInfo

    @Binds
    abstract fun bindsInferenceSettingsSource(source: SettingsInferenceSource): InferenceSettingsSource

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
