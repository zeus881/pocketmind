package com.sanjay.pocketmind.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sanjay.pocketmind.LaunchRequests
import com.sanjay.pocketmind.MainActivity
import com.sanjay.pocketmind.R
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.data.chat.ChatRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Home-screen widget: "New chat" plus the three most recent chats.
 *
 * Built with Jetpack Glance: the UI is written like Compose, and Glance translates it into the
 * RemoteViews the launcher draws (widgets don't run our code continuously). The data is read
 * once per update, here in [provideGlance]; MainActivity triggers an update when the user leaves
 * the app.
 *
 * Privacy: with the app lock on, no chat titles appear on the home screen, only "New chat".
 */
class PocketMindWidget :
    GlanceAppWidget(),
    KoinComponent {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Widgets aren't created by the DI container, so they look their dependencies up in Koin.
        val locked = get<SettingsDataSource>().current().appLockEnabled
        val recent = if (locked) emptyList() else get<ChatRepository>().observeChats("").first().take(RECENT_CHATS)
        provideContent {
            GlanceTheme { WidgetContent(context, recent, locked) }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, recent: List<ChatSummary>, locked: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(PmSpacing.l)
                .padding(PmSpacing.m),
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = TITLE_SP.sp,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            Button(
                text = context.getString(R.string.widget_new_chat),
                onClick = actionStartActivity(launchIntent(context, LaunchRequests.ACTION_NEW_CHAT)),
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = PmSpacing.s),
            )
            when {
                locked -> Hint(context.getString(R.string.widget_locked))

                recent.isEmpty() -> Hint(context.getString(R.string.widget_no_chats))

                else -> recent.forEach { summary ->
                    Text(
                        text = summary.chat.title.ifBlank { context.getString(R.string.widget_untitled) },
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = PmSpacing.xs)
                            .clickable(actionStartActivity(openChatIntent(context, summary.chat.id))),
                    )
                }
            }
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
    }

    private companion object {
        const val RECENT_CHATS = 3
        const val TITLE_SP = 16
    }
}

/** Opens [MainActivity] with one of the [LaunchRequests] actions. */
internal fun launchIntent(context: Context, action: String): Intent = Intent(context, MainActivity::class.java)
    .setAction(action)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * The chat id is ALSO put in the Intent's data: Android treats two PendingIntents that differ only
 * in extras as the same one, so without a distinct URI every row would open the first chat.
 */
internal fun openChatIntent(context: Context, chatId: String): Intent =
    launchIntent(context, LaunchRequests.ACTION_OPEN_CHAT)
        .setData("pocketmind://chat/$chatId".toUri())
        .putExtra(LaunchRequests.EXTRA_CHAT_ID, chatId)

/** The broadcast receiver Android talks to; Glance does the rest. */
class PocketMindWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PocketMindWidget()
}
