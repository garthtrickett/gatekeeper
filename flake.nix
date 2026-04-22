{
  description = "The Gatekeeper - KMP Cognitive Orthotic (Android, Native Linux Daemon, Desktop UI)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "aarch64-linux"; # Targeting Asahi Linux / Apple Silicon

      # 1. Native packages (ARM)
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      # 2. x86_64 packages (Source of libs for QEMU emulation if needed for Android)
      pkgsX86 = import nixpkgs {
        system = "x86_64-linux";
        config.allowUnfree = true;
      };

      # 3. Android SDK Composition
      androidComposition = pkgs.androidenv.composeAndroidPackages {
        cmdLineToolsVersion = "11.0";
        platformToolsVersion = "35.0.2";
        buildToolsVersions = [ "35.0.0" "34.0.0" ];
        includeEmulator = false;
        platformVersions = [ "35" "34" ];
        includeSources = true;
        includeSystemImages = false;
      };

      androidSdk = androidComposition.androidsdk;

      # --- GATEKEEPER APP PACKAGING ---

      # Create a minimal, stripped-down JRE for the Desktop UI using jlink.
      # This removes the need for the user to have a full JDK installed.
      customJre = pkgs.runCommand "gatekeeper-jre"
        {
          nativeBuildInputs = [ pkgs.jdk21 pkgs.binutils ];
        } ''
        jlink \
          --module-path ${pkgs.jdk21}/lib/openjdk/jmods \
          --add-modules java.base,java.desktop,java.naming,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported \
          --compress=zip-9 \
          --no-header-files \
          --no-man-pages \
          --strip-debug \
          --output $out
      '';

      # Main Gatekeeper package derivation
      gatekeeperPkg = pkgs.stdenv.mkDerivation {
        pname = "gatekeeper";
        version = "1.0.0-MVP";
        src = ./.;

        nativeBuildInputs = [
          pkgs.gradle
          pkgs.jdk21
          pkgs.makeWrapper
          pkgs.pkg-config
          pkgs.unzip
        ];

        buildInputs = [
          pkgs.sqlite
          pkgs.alsa-lib
          pkgs.libx11
          pkgs.libxcursor
          pkgs.libxext
          pkgs.libxrandr
          pkgs.libxrender
          pkgs.libxi
          pkgs.libGL
          pkgs.fontconfig
          pkgs.freetype
          pkgs.wayland
          pkgs.libxkbcommon
          pkgs.mesa
        ];

        # Build the UberJar (UI)
        buildPhase = ''
          export GRADLE_USER_HOME=$(mktemp -d)
          
          echo "🔨 Compiling Kotlin/JVM Desktop UI..."
          gradle :app:packageReleaseUberJarForCurrentOS --no-daemon
        '';

        installPhase = ''
                              mkdir -p $out/bin
                              mkdir -p $out/lib/gatekeeper
                              mkdir -p $out/share/applications
                              mkdir -p $out/share/icons/hicolor/512x512/apps

                              # 1. Install the JVM UI Uber JAR
                              # Note: The filename depends on your gradle project versioning
                              cp app/build/compose/jars/*.jar $out/lib/gatekeeper/gatekeeper-ui.jar

                              # 2. Create the UI Wrapper Script
                              # This ensures the UI runs using the bundled custom JRE and has access to graphics
                              makeWrapper ${customJre}/bin/java $out/bin/gatekeeper \
                                --prefix LD_LIBRARY_PATH : "${pkgs.lib.makeLibraryPath (with pkgs;[ libx11 libxcursor libxext libxrandr libxrender libxi libGL fontconfig freetype wayland libxkbcommon mesa ])}" \
                                --add-flags "-Djava.awt.headless=false" \
                                --add-flags "-jar $out/lib/gatekeeper/gatekeeper-ui.jar"

                              # 3. Generate the .desktop entry for your application launcher
                              cat > $out/share/applications/gatekeeper.desktop <<EOF
          [Desktop Entry]
                    Version=1.0
                    Type=Application
                    Name=The Gatekeeper
                    Comment=System-Level Cognitive Orthotic
                    Exec=$out/bin/gatekeeper
                    Icon=security-high
                    Terminal=false
                    Categories=Utility;Security;
                    StartupWMClass=Gatekeeper
                    EOF
        '';
      };

    in
    {
      # Standard Package Output
      packages.${system}.default = gatekeeperPkg;

      # Home Manager Module Output
      # This allows you to enable gatekeeper in your NixOS config like:
      # services.gatekeeper.enable = true;
      homeManagerModules.default = { config, lib, pkgs, ... }: {
        options.services.gatekeeper = {
          enable = lib.mkEnableOption "Gatekeeper Cognitive Orthotic System";
          package = lib.mkOption {
            type = lib.types.package;
            default = self.packages.${pkgs.system}.default;
            description = "The Gatekeeper package to use.";
          };
        };

        config = lib.mkIf config.services.gatekeeper.enable {
          home.packages = [ config.services.gatekeeper.package ];
        };
      };

      # Developer Shell Output
      devShells.${system}.default = pkgs.mkShell {
        nativeBuildInputs = with pkgs; [
          python3
          bashInteractive
          pkg-config
        ];

        buildInputs = with pkgs;[
          # Native Tooling
          sqlite
          jdk21
          gradle
          unzip
          curl
          docker
          docker-compose
          jq

          # Desktop UI Runtime Libs (Skiko dependencies)
          libx11
          libxcursor
          libxext
          libxrandr
          libxrender
          libxi
          libGL
          fontconfig
          freetype
          wayland
          libxkbcommon
          mesa

          # Android SDK & Tools
          android-tools
          androidSdk

          # Language Servers for Helix
          kotlin-language-server
          ktlint
        ];

        shellHook = ''
                    echo "🚀 The Gatekeeper Dev Environment Loaded"
                    echo "Nix-built JRE & Native Tooling Ready"

                    # Android Config
                    export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
                    export ANDROID_HOME=$ANDROID_SDK_ROOT
                    export GOOGLE_APPLICATION_CREDENTIALS="$PWD/backend/service-account.json"
                    export JAVA_HOME="${pkgs.jdk21}/lib/openjdk"
                    alias adb="${pkgs.android-tools}/bin/adb"
                    alias desktop='gradle :app:run'
                    alias backend='./gradlew :backend:run -Dio.ktor.development=true'


                    alias logs='adb logcat | grep -iE "Gatekeeper|AndroidRuntime|WindowManager|FATAL"'
                    alias deploy='adb reverse tcp:8081 tcp:8081 && gradle installDebug && adb logcat -c && echo "✅ Deployed & Port 8081 Reversed. Waiting for logs..." && logs'
                    alias backend-logs='docker-compose logs -f'
                    alias lint='ktlint "app/src/**/*.kt"'
                    alias format='ktlint --format "app/src/**/*.kt"'
                    alias wipe='adb shell pm clear com.aegisgatekeeper.app'
                    alias test-unit='gradle :app:test'
                    alias test-ui='adb logcat -c && (adb logcat -s Gatekeeper & LOG_PID=$!; gradle :app:connectedAndroidTest; kill $LOG_PID)'

                    # E2E Orchestration Pipeline
                    dev-sync() {
                      cat << 'EOF' > /tmp/dev-sync.sh
          #!/usr/bin/env bash
          echo "🚀 Starting Cross-Platform Dev Sync..."
          trap 'echo "🛑 Stopping dev-sync..."; kill 0; adb reverse --remove tcp:8081' EXIT

          # Clean up previous state for a fresh start
          rm -f gatekeeper_backend.db
          adb shell pm clear com.aegisgatekeeper.app || true

          # Force local sync server URL for the Android emulator/device via adb reverse
          touch local.properties
          grep -v "^SYNC_SERVER_URL=" local.properties > local.properties.tmp || true
          echo "SYNC_SERVER_URL=http://localhost:8081" >> local.properties.tmp
          mv local.properties.tmp local.properties

          export DEV_MODE=true
          gradle :backend:run -Dio.ktor.development=true &

          echo "⏳ Waiting for Backend on :8081..."
          until curl -s http://localhost:8081/ > /dev/null; do sleep 1; done
          echo "✅ Backend Ready!"

          DEV_TOKEN=$(curl -s http://localhost:8081/auth/dev-token | jq -r .token)
          export GATEKEEPER_DEV_TOKEN=$DEV_TOKEN

          adb reverse tcp:8081 tcp:8081
          echo "✅ ADB Tunnel Established"

          echo "📱 Deploying & logging in Android App..."
          gradle :app:installDebug
          adb shell am start -n com.aegisgatekeeper.app/.MainActivity
          sleep 4
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action LOGIN -e token '$DEV_TOKEN'"

          echo "💻 Starting & logging in Desktop UI..."
          gradle :app:run &

          echo "🎉 All systems GO! Android and Desktop are running and logged into the same account."
          echo "Press Ctrl+C to stop all processes."
          wait
          EOF
                      bash /tmp/dev-sync.sh
                    }

                    test-sync-e2e() {
                      cat << 'EOF' > /tmp/test-sync-e2e.sh
          #!/usr/bin/env bash
          echo "🚀 Starting E2E Cross-Platform Sync Flow..."
          trap 'echo "🛑 Stopping E2E flow..."; kill 0; adb reverse --remove tcp:8081' EXIT

          # Clean up previous backend database to avoid duplicate items
          rm -f gatekeeper_backend.db

          # Force local sync server URL for the Android emulator/device via adb reverse
          touch local.properties
          grep -v "^SYNC_SERVER_URL=" local.properties > local.properties.tmp || true
          echo "SYNC_SERVER_URL=http://localhost:8081" >> local.properties.tmp
          mv local.properties.tmp local.properties

          export DEV_MODE=true
          gradle :backend:run -Dio.ktor.development=true &

          echo "⏳ Waiting for Backend on :8081..."
          until curl -s http://localhost:8081/ > /dev/null; do sleep 1; done
          echo "✅ Backend Ready!"

          DEV_TOKEN=$(curl -s http://localhost:8081/auth/dev-token | jq -r .token)
          export GATEKEEPER_DEV_TOKEN=$DEV_TOKEN

          adb reverse tcp:8081 tcp:8081
          echo "✅ ADB Tunnel Established"

          echo "📱 Deploying Android App..."
          gradle :app:installDebug
          adb shell pm clear com.aegisgatekeeper.app || true
          adb shell am start -n com.aegisgatekeeper.app/.MainActivity
          sleep 4

          echo "🔐 Auto-Logging in Android App..."
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action LOGIN -e token '$DEV_TOKEN'"

          echo "💻 Starting Desktop UI in background..."
          rm -f /tmp/desktop-e2e.log
          gradle :app:run > /tmp/desktop-e2e.log 2>&1 &
          DESKTOP_PID=$!
          sleep 6

          echo "📝 Creating Vault & Content Items on Android via ADB..."
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action CREATE_VAULT -e query 'E2E Auto-Sync Test Item'"
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action CREATE_CONTENT -e videoId 'dQw4w9WgXcQ'"

          echo "🔄 Forcing Android to Push Changes..."
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action FORCE_SYNC"

          echo "⏳ Waiting for Desktop app to pull BOTH items..."
          TIMEOUT=30
          until (grep -q "Desktop received synced Vault Item!" /tmp/desktop-e2e.log && grep -q "Desktop received synced Content Item!" /tmp/desktop-e2e.log) || [ $TIMEOUT -eq 0 ]; do
            sleep 1
            ((TIMEOUT--))
          done

          if [ $TIMEOUT -eq 0 ]; then
            echo "❌ E2E Flow Failed: Desktop did not receive initial sync."
            tail -n 20 /tmp/desktop-e2e.log
            exit 1
          fi

          echo "✅ Initial Sync verified! Now testing update/resolution propagation..."
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action RESOLVE_VAULT -e query 'E2E Auto-Sync Test Item'"
          adb shell "am broadcast -a com.aegisgatekeeper.E2E_ACTION -e action FORCE_SYNC"

          TIMEOUT=30
          until grep -q "Desktop received RESOLVED Vault Item!" /tmp/desktop-e2e.log || [ $TIMEOUT -eq 0 ]; do
            sleep 1
            ((TIMEOUT--))
          done

          if [ $TIMEOUT -eq 0 ]; then
            echo "❌ E2E Flow Failed: Desktop did not receive the update/resolution."
            tail -n 20 /tmp/desktop-e2e.log
            exit 1
          else
            echo "🎉 E2E Flow SUCCESS: Desktop received creations AND updates!"
            exit 0
          fi
          EOF
                      bash /tmp/test-sync-e2e.sh
                    }

                    # Local Linux Deployment Helpers
                    alias build-linux='gradle :app:packageReleaseUberJarForCurrentOS'
                    alias install-linux='build-linux && mkdir -p ~/.local/bin && cp app/build/compose/jars/*.jar ~/.local/bin/gatekeeper-ui.jar && printf "#!/bin/sh\nexport LD_LIBRARY_PATH=\"${pkgs.lib.makeLibraryPath (with pkgs;[ libx11 libxcursor libxext libxrandr libxrender libxi libGL fontconfig freetype wayland libxkbcommon mesa ])}:\$LD_LIBRARY_PATH\"\n${customJre}/bin/java -jar ~/.local/bin/gatekeeper-ui.jar \"\$@\"" > ~/.local/bin/gatekeeper && chmod +x ~/.local/bin/gatekeeper && echo "✅ Installed to ~/.local/bin/gatekeeper"'

                    # QEMU Compatibility for unpatched x86_64 binaries (AAPT2, etc)
                    QEMU_ROOT="$HOME/.local/share/qemu-x86-root"
                    mkdir -p "$QEMU_ROOT"
                    if [ ! -d "$QEMU_ROOT/lib64" ]; then
                       ln -s "${pkgsX86.glibc}/lib" "$QEMU_ROOT/lib64"
                       ln -s "${pkgsX86.glibc}/lib" "$QEMU_ROOT/lib"
                    fi
                    export QEMU_LD_PREFIX="$QEMU_ROOT"
                    # Provide CEF dependencies without polluting with x86 glibc, which breaks aarch64 native libraries
                    export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath (with pkgs;[ libx11 libxcursor libxext libxrandr libxrender libxi libGL fontconfig freetype wayland libxkbcommon mesa alsa-lib nss nspr atk cups dbus libdrm expat libxcomposite libxdamage pango cairo at-spi2-atk at-spi2-core glib ])}:$LD_LIBRARY_PATH"

                    # Helper Aliases
                    alias build-desktop='nix build .#default'

                    ${pkgs.gradle}/bin/gradle --stop >/dev/null 2>&1 || true
        '';
      };
    };
}
