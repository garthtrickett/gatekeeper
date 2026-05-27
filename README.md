# Aegis Gatekeeper

Aegis Gatekeeper is a cross-platform, local-first cognitive orthotic designed to help you reclaim your digital sovereignty. Built using Kotlin Multiplatform (KMP), Jetpack Compose, and SQLDelight, it enforces cognitive boundaries at the operating system level on Android and Desktop (Linux/macOS/Windows) [1, 2]. 

By substituting algorithmic feeds with curated, high-intent media, Aegis Gatekeeper transforms your devices from distraction engines into focused tools.

---

## System Architecture & Features

Aegis Gatekeeper relies on a unidirectional data flow based on the **State-Action-Model (SAM)** architecture [GEMINI.md]. All business logic is modeled as pure state transitions (Reducers), separating calculations from side-effects (ViewModel, database I/O, network requests) [GEMINI.md].

### 1. Dual-Moat Interceptor (Layer Alpha & Omega)
On Android, Gatekeeper operates a multi-layered background interceptor to block distracting applications before your brain receives a dopamine loop [GEMINI.md]:
*   **Layer Omega (Accessibility Service):** A low-latency, active window monitor that intercepts unauthorized app launches instantly [GatekeeperAccessibilityService.kt].
*   **Layer Alpha (Usage Stats Polling):** A persistent fallback background polling mechanism that periodically inspects system state via `UsageStatsManager` to ensure protection remains active even if accessibility services are restarted or paused by the OS [GatekeeperForegroundService.kt, ForegroundAppDetector.kt].
*   **Friction Engines:** To bypass a block, you must complete physically-modeled sensor tasks (guided by the device's gravity sensors or accelerometer) such as *The Gauntlet* (navigating a ball through a maze) or *Hold Steady* (retaining a ball in a target zone) [BallBalancingUi.kt, GyroscopeManager.kt].

### 2. Lookup Vault & Phase Boundaries
The system partitions your day into strict, configurable cognitive phases [VaultReviewScreen.kt, DesktopAccountScreen.kt]:
*   **Focus Phase (Deep Work):** The Lookup Vault is locked. Any urge to search or look up information is captured into the local vault and hidden, keeping you in your current context [VaultReviewScreen.kt].
*   **Gathering Phase (Discovery):** The Vault unlocks [VaultReviewScreen.kt]. You are presented with your captured thoughts and can systematically triage them through deliberate search options (Surgical Web, Clean YouTube, or Podcasts) [VaultReviewScreen.kt].

### 3. Surgical Web & Ad-Block Filtering
Both the Desktop client (via JCEF) and Android client (via WebView) implement a custom **Surgical Filter Engine** [SurgicalFilterEngine.kt, SurgicalWebScreen.kt, BaseSurgicalWebView.kt]:
*   **Network & Cosmetic Filter Compilation:** Parses standard AdGuard and uBlock Origin privacy lists, blocking telemetry/ad networks and injecting customized CSS to hide algorithm feeds, sidebars, and infinite scroll mechanics on platforms like YouTube, Twitter, and Substack [SurgicalFilterEngine.kt, SyncClient.kt].
*   **YouTube Jail:** Hard-blocks watch pages and YouTube Shorts [SurgicalJailIntegrationTest.kt]. You can only search for specific topics or watch curated, designated "Safe Channels" that you have explicitly whitelisted [SurgicalYouTubeScreen.kt, YouTubeSurgicalBridge.kt].

### 4. Sovereign Media Queue
*   **Intentional Slots:** Features a strict 5-slot queue modeled after physical SD cards [IntentionalAudioScreen.kt]. Changing slots incurs high friction, encouraging commit-to-listen behaviors [IntentionalAudioScreen.kt].
*   **Offline Downloads & Native Playback:** Audio from RSS feeds and podcasts can be downloaded directly for offline, distraction-free listening using native ExoPlayer/Media3 services with media session integration [GatekeeperDownloadService.kt, PodcastMediaService.kt].

### 5. Messaging Outpost
Integrates directly with Beeper (via Android ContentProviders) to allow outbound-only, high-intent communication [AndroidBeeperClient.kt]. You can write and queue messages to be delivered with a custom delay, allowing you to respond to people without being exposed to incoming chat list distractions [OutpostScreen.kt].

### 6. Sovereign Sync Backend
A lightweight, self-hostable Kotlin/Ktor server that manages end-to-end state synchronization [Application.kt]:
*   Supports secure magic-link authentication [AuthRoutes.kt].
*   Syncs Lookup Vault entries, Content Bank queues, and custom configurations across active devices [SyncRoutes.kt].
*   Utilizes Firebase Cloud Messaging (FCM) to trigger silent background "sync pokes" between your Android device and Desktop client when changes are pushed [SyncRoutes.kt, GatekeeperFcmService.kt].

---

## Project Structure

*   **`:app`**: KMP target containing Jetpack Compose UI, Android Services, and Desktop JVM targets [build.gradle.kts].
*   **`:backend`**: Self-hostable Ktor synchronisation backend [build.gradle.kts].
*   **`:common-shared`**: Shared Kotlin Multiplatform DTOs and serialization models [build.gradle.kts].

---

## Getting Started (Nix Environment)

This project contains a comprehensive, reproducible `flake.nix` that bundles the Android SDK, JDK 21, Gradle, and all necessary native rendering libraries [flake.nix].

### Developer Shell
Enter the development environment by running:
```bash
nix develop
```

Once inside the shell, several convenient aliases are registered [flake.nix]:

*   `desktop`: Runs the Kotlin/JVM Desktop UI [flake.nix].
*   `backend`: Runs the Ktor synchronization backend locally [flake.nix].
*   `apk`: Builds the debug APK files for development and production [flake.nix].
*   `deploy-dev`: Installs the development flavor, establishes adb port reverses, and opens a Logcat pipeline [flake.nix].
*   `deploy-prod`: Installs the production flavor with default branding [flake.nix].
*   `update-filters`: Automatically pulls down the latest AdGuard and uBlock Origin filter lists into project resources [flake.nix].
*   `test-unit`: Runs local JUnit tests [flake.nix].
*   `test-ui`: Executes instrumented Android device tests [flake.nix].

### Local E2E Orchestration Pipeline
You can spin up the entire multi-platform ecosystem (Sync Backend, Desktop UI, and Android App connected via ADB) in a single command [flake.nix]:

```bash
# Spins up the backend, Desktop UI, and reverses ports to the emulator
dev-sync

# Runs the complete end-to-end synchronization tests
test-sync-e2e
```

---

## Production Deployment (NixOS)

The system includes a Home Manager module to run the Desktop UI natively [flake.nix]. Add this repository as an input to your flake config and enable the service:

```nix
services.gatekeeper.enable = true;
```

---

## Code Style & Standards

Contributors are expected to adhere to the standards outlined in `GEMINI.md`:
1.  **Strict Immutability:** Never use `var` unless highly localized. Use immutable collections [GEMINI.md].
2.  **State-Action-Model Loop:** All UI-driven features must follow the unidirectional loop governed by the Reducers [GEMINI.md].
3.  **No Web-Jank:** Core layouts must remain strictly native Compose to ensure deep system integration [GEMINI.md].
4.  **No OO-Managers:** Business logic belongs in top-level pure functions and extension functions [GEMINI.md]. Use classes solely for system capabilities [GEMINI.md].
