The Gatekeeper: Cognitive Orthotic System

The Gatekeeper is a high-performance system designed to reclaim digital sovereignty. It consists of a native Android interceptor, a Compose Multiplatform dashboard with surgical web filtering, and an offline-first synchronization backend.

🛠 Prerequisites

This project uses a Nix-based development environment to manage the Android SDK, JDK 21, Kotlin Language Server, and all native dependencies.

Install Nix: Official Installation Guide

Enter the Dev Shell: Run nix develop in the project root. This ensures all aliases and tools are available in your path.

Setup API Keys: Create a local.properties file in the root and add your YouTube Data API v3 key:

code
Properties
download
content_copy
expand_less
YOUTUBE_API_KEY=your_key_here
☁️ Backend: Sovereign Sync

The backend manages magic-link authentication and secure state synchronization using Ktor and PostgreSQL.

1. Start the Database

Spin up the local PostgreSQL instance via Docker:

code
Bash
download
content_copy
expand_less
docker-compose up -d
2. Run the Server
code
Bash
download
content_copy
expand_less
./gradlew :backend:run

The server starts at http://0.0.0.0:8080.

Note on Authentication: In development, magic link emails are not sent. Instead, check the backend console logs; the verification URL will be printed there (e.g., http://localhost:8080/auth/verify-token?token=...).

📱 Android: Local Development (USB)

The Android app features a Dual-Moat interceptor: Layer Alpha (Usage Stats polling) and Layer Omega (Accessibility Service).

1. Connect Device

Enable Developer Options and USB Debugging on your phone.

Connect via USB and verify with adb devices.

2. Deploy and Monitor

Use the dev-shell alias to build, install, and stream filtered logs:

code
Bash
download
content_copy
expand_less
deploy
3. Permissions ("The Final Boss")

On first launch, you must manually grant four critical permissions to enable the interceptor:

Display over other apps (The Moat UI).

Usage Access (Time tracking).

Accessibility Service (Zero-latency blocking).

Battery Optimization -> Unrestricted (Essential for background persistence).

💻 Desktop: Sovereignty Dashboard

The desktop app provides the Surgical Web Engine (Chromium-based filtering) and a full view of your Content Bank.

Development Mode (Gradle)

To run the app via Gradle for active UI development:

code
Bash
download
content_copy
expand_less
dash
Permanent Local Install (Linux/Asahi)

To avoid Gradle's startup overhead and ensure hardware acceleration on Linux, use the installation aliases:

Build & Map Dependencies: install-linux

Run Anywhere: Just type gatekeeper in any terminal.

⌨️ Command Reference (Aliases)

All commands must be run inside the nix develop shell:

Alias	Description
deploy	Build, install to phone, and stream Gatekeeper logs.
dash	Launch the Desktop UI via Gradle (:app:run).
logs	Stream Logcat filtered for the Gatekeeper tag.
wipe	Clear all app data on the connected phone.
format	Run ktlint to enforce functional coding standards.
test-unit	Run JVM unit tests (Reducer logic).
test-ui	Run instrumented Android UI tests.
install-linux	Build UberJar and install a native-entry binary to ~/.local/bin.
🏗 Architecture & Standards

Logic: Strict State-Action-Model (SAM) loop. All logic flows through pure reduce functions.

Persistence: SQLDelight for compile-time safe SQLite queries.

Style: Functional Kotlin (strict immutability, val by default).

Logging: All logs are category-prefixed with emojis for visual parsing:

📥 Action Dispatched

🔄 State Updated

⚙️ Side-Effect Triggered

📡 Network Call

👁️ Background Observation

See GEMINI.md for the full technical specification.
