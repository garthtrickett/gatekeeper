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
                val fallbackTitle = sharedText.replace(url, "").trim().ifEmpty { null }

                GatekeeperStateManager.dispatch(
                    GatekeeperAction.ProcessSharedLink(
                        url = url,
                        providedTitle = fallbackTitle,
                        currentTimestamp = System.currentTimeMillis(),
                    ),
                )
                Toast.makeText(this, "Opening in Gatekeeper...", Toast.LENGTH_SHORT).show()

                // Launch the main activity to ensure the UI is visible for the dialog
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
        }
        finish()
    }
}
