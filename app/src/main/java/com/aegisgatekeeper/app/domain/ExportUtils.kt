package com.aegisgatekeeper.app.domain

/**
 * Pure function to generate the Markdown export of the user's digital footprint.
 * Extracted from the StateManager to ensure it can be strictly unit tested.
 */
fun generateMarkdownReport(
    vaultItems: List<VaultItem>,
    sessionLogs: List<SessionLog>,
    bypassCount: Int,
    giveUpCount: Int,
): String {
    val sb = java.lang.StringBuilder()
    sb.append("# Gatekeeper Digital Sovereignty Report\n\n")

    sb.append("## Metrics\n")
    sb.append("- Total Bypasses: $bypassCount\n")
    sb.append("- Total Give-Ups: $giveUpCount\n")
    sb.append("- Sessions Logged: ${sessionLogs.size}\n\n")

    sb.append("## Lookup Vault\n")
    vaultItems.forEach {
        sb.append("- [${if (it.isResolved) "x" else " "}] ${it.query} (Captured: ${it.capturedAtTimestamp})\n")
    }

    sb.append("\n## Session Logs\n")
    sessionLogs.forEach {
        sb.append("- ${it.packageName}: ${it.durationMillis / 1000}s, Emotion: ${it.emotion}\n")
    }

    return sb.toString()
}
