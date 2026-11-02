package com.sanjay.pocketmind.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sanjay.pocketmind.LaunchRequests

/**
 * Quick-settings tile "PocketMind": one tap from the notification shade to a new chat.
 *
 * If the phone is locked, Android first asks the user to unlock (unlockAndRun): the tile must
 * never open private chats over the lock screen.
 */
class NewChatTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE // an action, not an on/off switch
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openNewChat) else openNewChat()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent overload is the only one before Android 14
    private fun openNewChat() {
        val intent = launchIntent(this, LaunchRequests.ACTION_NEW_CHAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
