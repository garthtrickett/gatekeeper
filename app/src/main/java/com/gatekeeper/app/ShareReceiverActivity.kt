package com.gatekeeper.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.gatekeeper.app.domain.GatekeeperAction

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                // Extract URL if the shared text contains extra words
                val urlRegex = """(https?://[^\s]+)""".toRegex()
                val url = urlRegex.find(sharedText)?.value ?: sharedText

                GatekeeperStateManager.dispatch(
                    GatekeeperAction.ProcessSharedLink(
                        url = url,
                        currentTimestamp = System.currentTimeMillis()
                    )
                )
                Toast.makeText(this, "Saved to Content Bank", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
