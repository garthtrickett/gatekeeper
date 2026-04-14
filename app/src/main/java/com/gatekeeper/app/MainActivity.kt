package com.gatekeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Intentionally left blank. 
        // UI tests use createAndroidComposeRule<MainActivity>() and will 
        // call setContent {} themselves. Calling it here causes an IllegalStateException.
    }
}
