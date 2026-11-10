package com.sanjay.pocketmind.play

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManager
import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * When to ask for a Play review: only after the user got real value (the 5th completed answer), and
 * at most once every 90 days. Play adds its own limits and may show nothing at all; that is expected
 * and never an error. The app never asks "do you like us?" itself and never rewards a rating.
 */
object ReviewPolicy {
    const val ANSWERS_BEFORE_ASKING = 5
    const val DAYS_BETWEEN_ASKS = 90L
    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    fun shouldAsk(completedAnswers: Int, lastAskedAt: Long?, now: Long): Boolean =
        completedAnswers >= ANSWERS_BEFORE_ASKING &&
            (lastAskedAt == null || now - lastAskedAt >= DAYS_BETWEEN_ASKS * MILLIS_PER_DAY)
}

/** Google Play's In-App Review card, shown in the app by the Play Store app (no data of ours is sent). */
@Singleton
class InAppReview @Inject constructor(
    private val manager: ReviewManager,
    private val settings: SettingsDataSource,
    private val clock: Clock,
) {
    /** Called for every completed answer; asks Play for the review card when [ReviewPolicy] allows. */
    @Suppress("TooGenericExceptionCaught") // no Play Store, quota reached…: never a reason to bother the user
    suspend fun onAnswerCompleted(activity: Activity) {
        val answers = settings.recordCompletedAnswer()
        val now = clock.nowMillis()
        if (!ReviewPolicy.shouldAsk(answers, settings.reviewAskedAt(), now)) return
        settings.setReviewAskedAt(now) // counted as asked even if Play decides to show nothing
        try {
            manager.launchReview(activity, manager.requestReview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.i("No Play review card: %s", e.javaClass.simpleName)
        }
    }
}
