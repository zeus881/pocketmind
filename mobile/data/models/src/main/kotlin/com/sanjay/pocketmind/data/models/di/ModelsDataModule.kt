package com.sanjay.pocketmind.data.models.di

import androidx.work.WorkManager
import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.core.llm.EmbeddingModelSource
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.data.models.AndroidDeviceInfo
import com.sanjay.pocketmind.data.models.CatalogEmbeddingModelSource
import com.sanjay.pocketmind.data.models.DeviceInfo
import com.sanjay.pocketmind.data.models.ModelCatalog
import com.sanjay.pocketmind.data.models.ModelRepository
import com.sanjay.pocketmind.data.models.ModelVerifier
import com.sanjay.pocketmind.data.models.SettingsInferenceSource
import com.sanjay.pocketmind.data.models.WorkManagerModelRepository
import com.sanjay.pocketmind.data.models.download.HostAllowlist
import com.sanjay.pocketmind.data.models.download.ModelDownloader
import com.sanjay.pocketmind.data.models.work.ModelDownloadWorker
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

/** The model catalog, downloads (WorkManager + OkHttp) and device facts (V3: Koin; v2 used Hilt). */
val modelsDataModule = module {
    single { ModelCatalog(androidContext()) }
    factory<DeviceInfo> { AndroidDeviceInfo(androidContext(), get()) }
    factory<EmbeddingModelSource> { CatalogEmbeddingModelSource(get(), get()) }
    factory { ModelVerifier(get(), get(), get(), get(PmQualifiers.Io)) }
    factory { ModelDownloader(get(), get(), get(PmQualifiers.Io)) }
    // HostAllowlist: the ONLY hosts this client may reach are the model hosts (no other network use, ever).
    single {
        OkHttpClient.Builder()
            .addNetworkInterceptor(HostAllowlist)
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
    factory { WorkManager.getInstance(androidContext()) }
    workerOf(::ModelDownloadWorker)
}

/**
 * Which model is installed and how it is run. Separate from [modelsDataModule] so end-to-end tests can point the
 * app at a fake installed model and keep the rest real.
 */
val modelSelectionModule = module {
    single<ModelRepository> {
        WorkManagerModelRepository(get(), get(), get(), get(), get(), get(), get(), get(PmQualifiers.Io), get())
    }
    factory<InferenceSettingsSource> { SettingsInferenceSource(get(), get(), get()) }
}

private const val CONNECT_TIMEOUT_S = 30L
private const val READ_TIMEOUT_S = 60L
