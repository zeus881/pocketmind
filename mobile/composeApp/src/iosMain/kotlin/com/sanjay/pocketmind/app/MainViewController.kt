package com.sanjay.pocketmind.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point for iOS: the Swift app (iosApp/) puts this view controller on screen.
 * Swift sees it as `MainViewControllerKt.MainViewController()`.
 */
@Suppress("FunctionName") // Named like a type on purpose: Swift code treats it as a view-controller factory.
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
