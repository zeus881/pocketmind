package com.sanjay.pocketmind.play

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Where a Play update stands, as far as the UI cares. */
enum class UpdateState {
    /** Nothing to do (no update, the user said no, or the app wasn't installed from Play). */
    NONE,

    /** Play is downloading the update in the background; the app keeps working. */
    DOWNLOADING,

    /** Downloaded: the user can restart into the new version now. */
    READY_TO_INSTALL,
}

/**
 * Google Play's In-App Updates, FLEXIBLE flow: Play asks the user once, downloads the update in the
 * background while they keep chatting, and we offer "Restart" when it's ready. The download runs in
 * the Play Store app, not in PocketMind, and sends no user data (approved decision 5).
 *
 * Installed from GitHub or Android Studio (no Play Store entry)? Every call simply fails, and the
 * failure is ignored: updates then come from wherever the app came from.
 */
class InAppUpdates(private val manager: AppUpdateManager) {
    private val _state = MutableStateFlow(UpdateState.NONE)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val listener = InstallStateUpdatedListener { install ->
        _state.value = when (install.installStatus()) {
            InstallStatus.DOWNLOADED -> UpdateState.READY_TO_INSTALL
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> UpdateState.DOWNLOADING
            else -> UpdateState.NONE
        }
    }

    /**
     * Asks Play whether a newer version exists and, if so, shows Play's update dialog through
     * [launcher]. [beforeDialog] runs right before the dialog opens (the lock timer must ignore it).
     */
    @Suppress("TooGenericExceptionCaught") // Play reports "not installed from Play" etc. as various exceptions
    suspend fun check(launcher: ActivityResultLauncher<IntentSenderRequest>, beforeDialog: () -> Unit = {}) {
        try {
            val info = manager.requestAppUpdateInfo()
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                _state.value = UpdateState.READY_TO_INSTALL // downloaded in an earlier session
                return
            }
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            if (available && info.isFlexibleUpdateAllowed) {
                manager.registerListener(listener)
                beforeDialog()
                val flexible = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                manager.startUpdateFlowForResult(info, launcher, flexible)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.i("No Play update check: %s", e.javaClass.simpleName)
        }
    }

    /** "Restart": Play installs the downloaded update and restarts the app. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun install() {
        try {
            manager.requestCompleteUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Play could not install the update")
        } finally {
            manager.unregisterListener(listener)
        }
    }

    /** "Later": hide the bar for this session; Play installs the update by itself when the app is closed. */
    fun postpone() {
        _state.value = UpdateState.NONE
    }
}
