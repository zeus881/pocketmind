package com.sanjay.pocketmind.core.billing.di

import com.sanjay.pocketmind.core.billing.BillingGateway
import com.sanjay.pocketmind.core.billing.PlayBillingGateway
import com.sanjay.pocketmind.core.billing.PlayProStatus
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** PocketMind Pro through Google Play Billing. End-to-end tests replace this module with a fake. */
val billingModule = module {
    single<BillingGateway> { PlayBillingGateway(androidContext()) }
    single<ProStatus> { PlayProStatus(get(), get(), get(PmQualifiers.AppScope)) }
}
