package com.sanjay.pocketmind.core.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * Opens the platform's own share sheet with [text] (Android: the "Share" chooser; iOS: UIActivityViewController).
 * [title] is the chooser heading on Android; iOS shows no title.
 *
 * Why a composable that returns a function: both platforms need something only the UI has (Android an Activity
 * context, iOS the current UIViewController), so the screen "remembers" a sharer while it is composed and calls it
 * from an event handler. The app itself never sends the text anywhere: the user picks the destination.
 */
@Composable
expect fun rememberTextSharer(): (text: String, title: String) -> Unit

/**
 * [epochMillis] as a date and time in the phone's language and format, e.g. "11 Nov 2026, 14:05"
 * (medium date + short time, the same style v2 used on Android).
 */
expect fun formatDateTime(epochMillis: Long): String

/**
 * How long ago [epochMillis] was, in the phone's language: "5 minutes ago", "Yesterday", or a date for older
 * times. Android keeps v2's wording (DateUtils); iOS uses the system's NSRelativeDateTimeFormatter.
 */
expect fun formatRelativeTime(epochMillis: Long): String

/**
 * Formats a byte count the way the phone shows file sizes ("2.6 GB"). A composable that returns a function
 * because Android's formatter needs a Context, while callers also use it in event handlers (snackbars).
 */
@Composable
expect fun rememberFileSizeFormatter(): (bytes: Long) -> String

/**
 * Asks for permission to show download-progress notifications, then calls [onAnswered] whatever the user chose.
 * Returns null when no question is needed: Android before 13 (always allowed) and iOS (no download notifications
 * yet; the iOS downloader comes in V3 phase 2d), so the caller simply goes ahead.
 */
@Composable
expect fun rememberNotificationPermissionLauncher(onAnswered: () -> Unit): (() -> Unit)?
