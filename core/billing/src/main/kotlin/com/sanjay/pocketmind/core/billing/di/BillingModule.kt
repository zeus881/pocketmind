package com.sanjay.pocketmind.core.billing.di

import com.sanjay.pocketmind.core.billing.BillingGateway
import com.sanjay.pocketmind.core.billing.PlayBillingGateway
import com.sanjay.pocketmind.core.billing.PlayProStatus
import com.sanjay.pocketmind.core.billing.ProStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BillingModule {
    @Binds
    abstract fun bindsBillingGateway(gateway: PlayBillingGateway): BillingGateway

    @Binds
    abstract fun bindsProStatus(status: PlayProStatus): ProStatus
}
