package com.sanjay.pocketmind.core.billing

/**
 * The screen a store purchase sheet opens over: an Activity on Android (Play Billing needs one), a
 * UIViewController on iOS. A typealias on each platform, so Android code keeps passing its Activity unchanged.
 */
expect open class PurchaseHost
