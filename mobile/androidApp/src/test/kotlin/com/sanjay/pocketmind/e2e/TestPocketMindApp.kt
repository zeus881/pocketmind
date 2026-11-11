package com.sanjay.pocketmind.e2e

import com.sanjay.pocketmind.PocketMindApp
import org.koin.core.module.Module

/** The real app with [endToEndTestModule] added last, so its fakes override the phone-only definitions. */
class TestPocketMindApp : PocketMindApp() {
    override fun koinModules(): List<Module> = super.koinModules() + endToEndTestModule
}
