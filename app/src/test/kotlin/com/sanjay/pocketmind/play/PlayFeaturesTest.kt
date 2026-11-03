package com.sanjay.pocketmind.play

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.testing.FakeReviewManager
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** In-App Update and In-App Review with Google's own fakes; the review rule as plain arithmetic. */
@RunWith(RobolectricTestRunner::class)
class PlayFeaturesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `the review is asked after five answers and then not again for 90 days`() {
        assertFalse(ReviewPolicy.shouldAsk(completedAnswers = 4, lastAskedAt = null, now = 0))
        assertTrue(ReviewPolicy.shouldAsk(completedAnswers = 5, lastAskedAt = null, now = 0))
        assertFalse(ReviewPolicy.shouldAsk(completedAnswers = 50, lastAskedAt = 0, now = 89 * day))
        assertTrue(ReviewPolicy.shouldAsk(completedAnswers = 50, lastAskedAt = 0, now = 90 * day))
    }

    @Test
    fun `the review card is requested on the fifth completed answer only`() = runTest {
        val settings = SettingsDataSource(InMemoryPreferencesDataStore())
        val review = InAppReview(FakeReviewManager(context), settings) { 1_000L }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        repeat(4) { review.onAnswerCompleted(activity) }
        assertNull(settings.reviewAskedAt())

        review.onAnswerCompleted(activity)
        assertEquals(1_000L, settings.reviewAskedAt())
    }

    /** A launcher that only records that the update dialog was opened. */
    private class RecordingLauncher : ActivityResultLauncher<IntentSenderRequest>() {
        var launches = 0
        override val contract: ActivityResultContract<IntentSenderRequest, *> =
            ActivityResultContracts.StartIntentSenderForResult()

        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) {
            launches++
        }

        override fun unregister() = Unit
    }

    @Test
    fun `an available flexible update opens Play's dialog and offers restart once downloaded`() = runTest {
        val play = FakeAppUpdateManager(context).apply { setUpdateAvailable(2, AppUpdateType.FLEXIBLE) }
        val updates = InAppUpdates(play)
        var beforeDialog = 0

        updates.check(RecordingLauncher()) { beforeDialog++ }
        ShadowLooper.idleMainLooper()
        assertTrue(play.isConfirmationDialogVisible)
        assertEquals(1, beforeDialog)

        play.userAcceptsUpdate()
        play.downloadStarts()
        ShadowLooper.idleMainLooper()
        assertEquals(UpdateState.DOWNLOADING, updates.state.value)
        play.downloadCompletes()
        ShadowLooper.idleMainLooper()
        assertEquals(UpdateState.READY_TO_INSTALL, updates.state.value)

        updates.postpone()
        assertEquals(UpdateState.NONE, updates.state.value)
    }

    @Test
    fun `without an update nothing is shown`() = runTest {
        val play = FakeAppUpdateManager(context).apply { setUpdateNotAvailable() }
        val updates = InAppUpdates(play)

        updates.check(RecordingLauncher())
        ShadowLooper.idleMainLooper()

        assertFalse(play.isConfirmationDialogVisible)
        assertEquals(UpdateState.NONE, updates.state.value)
    }

    @Test
    fun `restart asks Play to install the downloaded update`() = runTest {
        val play = FakeAppUpdateManager(context).apply { setUpdateAvailable(2, AppUpdateType.FLEXIBLE) }
        val updates = InAppUpdates(play)
        updates.check(RecordingLauncher())
        play.userAcceptsUpdate()
        play.downloadStarts()
        play.downloadCompletes()
        ShadowLooper.idleMainLooper()

        updates.install()
        ShadowLooper.idleMainLooper()

        assertTrue(play.isInstallSplashScreenVisible)
    }

    /** Like an install from GitHub or Android Studio: Play refuses every request. */
    private class NoPlayStore(context: Context) : FakeAppUpdateManager(context) {
        override fun getAppUpdateInfo(): Task<AppUpdateInfo> = Tasks.forException(IllegalStateException("no Play"))

        override fun completeUpdate(): Task<Void> = Tasks.forException(IllegalStateException("no Play"))
    }

    @Test
    fun `without the Play Store updates and reviews fail quietly`() = runTest {
        val updates = InAppUpdates(NoPlayStore(context))
        updates.check(RecordingLauncher())
        updates.install()
        assertEquals(UpdateState.NONE, updates.state.value)

        val noReview = object : FakeReviewManager(context) {
            override fun requestReviewFlow(): Task<ReviewInfo> = Tasks.forException(IllegalStateException("no Play"))
        }
        val settings = SettingsDataSource(InMemoryPreferencesDataStore())
        val review = InAppReview(noReview, settings) { 7L }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        repeat(ReviewPolicy.ANSWERS_BEFORE_ASKING) { review.onAnswerCompleted(activity) }

        assertEquals(7L, settings.reviewAskedAt()) // counted as asked: Play is not asked again for 90 days
    }
}
