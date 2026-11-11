package com.sanjay.pocketmind.play

import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Google Play's in-app update and review clients (decision 5: they run through the Play Store app and send no
 * user content). End-to-end tests replace this module with fakes.
 */
val playModule = module {
    single<AppUpdateManager> { AppUpdateManagerFactory.create(androidContext()) }
    single<ReviewManager> { ReviewManagerFactory.create(androidContext()) }
}
