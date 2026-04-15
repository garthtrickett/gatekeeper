package com.gatekeeper.app

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
        return super.newApplication(cl, App::class.java.name, context)
    }
}
