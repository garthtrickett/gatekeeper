package com.aegisgatekeeper.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.aegisgatekeeper.app.domain.GatekeeperAction

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("Gatekeeper", "✅ ShareReceiverActivity: onCreate triggered")

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                android.util.Log.d("Gatekeeper", "📺 ShareReceiverActivity: Received shared text: $sharedText")
                // Extract URL if the shared text contains extra words
                val urlRegex = """(https?://[^\s"'<>]+)""".toRegex()
                val url = urlRegex.find(sharedText)?.value ?: sharedText

                GatekeeperStateManager.dispatch(
                    GatekeeperAction.ProcessSharedLink(
                        url = url,
                        currentTimestamp = System.currentTimeMillis(),
                    ),
                )
                Toast.makeText(this, "Saved to Content Bank", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
