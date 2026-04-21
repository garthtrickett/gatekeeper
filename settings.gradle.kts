pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jogamp.org/deployment/maven")
    }
}

// --- GLOBAL REFACTOR SCRIPT ---
// Resolves residual 'com.gatekeeper' package names and directories during configuration phase
listOf(
    "app/src",
    "backend/src",
    "common-shared/src",
    "app/build.gradle.kts",
    "backend/build.gradle.kts"
).map { rootProject.projectDir.resolve(it) }.filter { it.exists() }.forEach { target ->
    if (target.isDirectory) {
        target.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "sq", "xml", "kts", "json", "pro", "java") }
            .forEach { f ->
                val content = f.readText()
                if (content.contains("com.gatekeeper")) {
                    f.writeText(content.replace("com.gatekeeper", "com.aegisgatekeeper"))
                }
            }
        target.walkTopDown()
            .filter { it.isDirectory && it.name == "gatekeeper" && it.parentFile?.name == "com" }
            .toList()
            .forEach { dir ->
                dir.renameTo(java.io.File(dir.parentFile, "aegisgatekeeper"))
            }
    } else if (target.isFile) {
        val content = target.readText()
        if (content.contains("com.gatekeeper")) {
            target.writeText(content.replace("com.gatekeeper", "com.aegisgatekeeper"))
        }
    }
}
// --- END REFACTOR SCRIPT ---

rootProject.name = "Gatekeeper"
include(":app")
include(":backend")
include(":common-shared")
