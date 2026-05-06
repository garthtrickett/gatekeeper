package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.di.Singleton
import me.tatarka.inject.annotations.Inject

@Inject
@Singleton
class SurgicalFilterEngine {
    private var networkRules = mutableListOf<NetworkRule>()
    private var cosmeticRules = mutableListOf<CosmeticRule>()

    fun compile(rawRules: List<String>) {
        val newNetwork = mutableListOf<NetworkRule>()
        val newCosmetic = mutableListOf<CosmeticRule>()
        for (rule in rawRules) {
            val trimmed = rule.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!")) continue
            
            if (trimmed.contains("##")) {
                val parts = trimmed.split("##", limit = 2)
                val domains = parts[0].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val selector = parts[1].trim()
                newCosmetic.add(CosmeticRule(domains, selector))
            } else {
                var pattern = trimmed
                if (pattern.startsWith("||")) pattern = pattern.substring(2)
                if (pattern.endsWith("^")) pattern = pattern.dropLast(1)
                newNetwork.add(NetworkRule(pattern))
            }
        }
        networkRules = newNetwork
        cosmeticRules = newCosmetic
        platformLog("Gatekeeper", "🛡️ SurgicalFilterEngine: Compiled ${networkRules.size} network rules, ${cosmeticRules.size} cosmetic rules.")
    }

    fun shouldBlockRequest(url: String, documentUrl: String): Boolean {
        val lowerUrl = url.lowercase()
        for (rule in networkRules) {
            if (lowerUrl.contains(rule.pattern.lowercase())) {
                return true
            }
        }
        return false
    }

    fun getCosmeticCss(url: String): String {
        val lowerUrl = url.lowercase()
        val matchedSelectors = mutableSetOf<String>()
        for (rule in cosmeticRules) {
            if (rule.domains.isEmpty() || rule.domains.any { lowerUrl.contains(it.lowercase()) }) {
                matchedSelectors.add(rule.selector)
            }
        }
        if (matchedSelectors.isEmpty()) return ""
        return matchedSelectors.joinToString(", ") + " { display: none !important; }"
    }

    private data class NetworkRule(val pattern: String)
    private data class CosmeticRule(val domains: List<String>, val selector: String)
}
