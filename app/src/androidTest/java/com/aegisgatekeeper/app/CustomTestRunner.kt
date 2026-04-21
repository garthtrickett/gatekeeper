package com.aegisgatekeeper.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * A custom test runner that forces the use of our main Application class.
 * This is CRITICAL for ensuring that singletons which rely on `App.instance`
 * (like DatabaseManager and GatekeeperStateManager) are correctly initialized
 * before any test code runs, preventing `NoClassDefFoundError`.
 */
class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        // Ensure a clean slate for instrumented tests by deleting any existing database
        // before the Application (and GatekeeperStateManager singleton) initializes.
        context?.deleteDatabase("gatekeeper.db")
        return super.newApplication(cl, App::class.java.name, context)
    }
}
