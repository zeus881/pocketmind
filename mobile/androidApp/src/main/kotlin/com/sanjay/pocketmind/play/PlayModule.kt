package com.sanjay.pocketmind.play

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Play Store's update and review clients (tests use FakeAppUpdateManager / FakeReviewManager). */
@Module
@InstallIn(SingletonComponent::class)
object PlayModule {
    @Provides
    @Singleton
    fun providesAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context)

    @Provides
    @Singleton
    fun providesReviewManager(@ApplicationContext context: Context): ReviewManager =
        ReviewManagerFactory.create(context)
}
