#!/usr/bin/env bash
#
# split-repos.sh
#
# Creates the TrackFlow-Pro private repo scaffold from the current public repo.
# Run from the root of the TrackFlow public repo.
#
# Usage:
#   ./scripts/split-repos.sh [TARGET_DIR]
#
# TARGET_DIR defaults to ../TrackFlow-Pro

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PUBLIC_REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="${1:-$PUBLIC_REPO/../TrackFlow-Pro}"

if [ -d "$TARGET_DIR" ]; then
  echo "ERROR: $TARGET_DIR already exists. Remove it or choose a different path."
  exit 1
fi

echo "Creating TrackFlow-Pro at $TARGET_DIR ..."
mkdir -p "$TARGET_DIR"

# ── 1. Copy Adobe module directories ──────────────────────────────────────────

MODULES=(
  trackflow-provider-adobe-analytics
  trackflow-provider-adobe-edge
  trackflow-provider-adobe-analytics-ios
  trackflow-provider-adobe-edge-ios
)

for mod in "${MODULES[@]}"; do
  echo "  Copying $mod ..."
  cp -R "$PUBLIC_REPO/$mod" "$TARGET_DIR/$mod"
done

# ── 2. Copy buildSrc ─────────────────────────────────────────────────────────

echo "  Copying buildSrc ..."
cp -R "$PUBLIC_REPO/buildSrc" "$TARGET_DIR/buildSrc"

# ── 3. Copy Gradle wrapper ───────────────────────────────────────────────────

echo "  Copying Gradle wrapper ..."
mkdir -p "$TARGET_DIR/gradle"
cp -R "$PUBLIC_REPO/gradle/wrapper" "$TARGET_DIR/gradle/wrapper"
cp "$PUBLIC_REPO/gradlew" "$TARGET_DIR/gradlew"
cp "$PUBLIC_REPO/gradlew.bat" "$TARGET_DIR/gradlew.bat"
chmod +x "$TARGET_DIR/gradlew"

# ── 4. Create settings.gradle.kts ────────────────────────────────────────────

cat > "$TARGET_DIR/settings.gradle.kts" << 'SETTINGS_EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "trackflow-pro"
include(":trackflow-provider-adobe-analytics")
include(":trackflow-provider-adobe-edge")

// iOS provider modules (compile on macOS only)
include(":trackflow-provider-adobe-analytics-ios")
include(":trackflow-provider-adobe-edge-ios")
SETTINGS_EOF

# ── 5. Create root build.gradle.kts ──────────────────────────────────────────

cat > "$TARGET_DIR/build.gradle.kts" << 'BUILD_EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    kotlin("android") version "2.3.20" apply false
    kotlin("multiplatform") version "2.3.20" apply false
    kotlin("native.cocoapods") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.atomicfu") version "0.26.1" apply false
}
BUILD_EOF

# ── 6. Create gradle.properties ──────────────────────────────────────────────

cat > "$TARGET_DIR/gradle.properties" << 'PROPS_EOF'
# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.apple.xcodeCompatibility.nowarn=true
org.gradle.parallel=true
org.gradle.caching=true

# Android SDK versions
android.compileSdk=34
android.minSdk=24
android.targetSdk=34

# Kotlin
kotlin.code.style=official

# AndroidX
android.useAndroidX=true

# TrackFlow SDK version — keep in sync with the public TrackFlow repo
trackflow.version=1.0.0
trackflow.groupId=com.trackflow
PROPS_EOF

# ── 7. Patch Android build files: project dep → Maven Central dep ────────────

for mod in trackflow-provider-adobe-analytics trackflow-provider-adobe-edge; do
  BUILD_FILE="$TARGET_DIR/$mod/build.gradle.kts"
  sed -i '' 's|api(project(":trackflow-core"))|api("com.trackflow:trackflow-core:${findProperty("trackflow.version")}")|' "$BUILD_FILE"
  echo "  Patched $mod/build.gradle.kts"
done

# ── 8. Patch iOS build files: project dep → Maven Central dep + pod dep ──────

for mod in trackflow-provider-adobe-analytics-ios trackflow-provider-adobe-edge-ios; do
  BUILD_FILE="$TARGET_DIR/$mod/build.gradle.kts"
  sed -i '' 's|implementation(project(":trackflow-core"))|api("com.trackflow:trackflow-core:${findProperty("trackflow.version")}")|' "$BUILD_FILE"

  # Add pod("trackflow_core") dependency after existing pod declarations
  # Find the last pod(...) line and append after it
  sed -i '' '/pod("AEP.*$/a\
\
        // TrackFlow core pulled from private CocoaPods spec repo\
        pod("trackflow_core")
' "$BUILD_FILE"

  echo "  Patched $mod/build.gradle.kts"
done

# ── 9. Create .gitignore ─────────────────────────────────────────────────────

cat > "$TARGET_DIR/.gitignore" << 'IGNORE_EOF'
# Built application files
*.apk
*.aar
*.ap_
*.aab

# Java class files
*.class

# Generated files
bin/
gen/
out/

# Gradle files
.gradle/
build/
**/build/

# Local configuration
local.properties

# Android Studio
*.iml
.idea/
.DS_Store

# Signing files
*.jks
*.keystore

# Google Services
google-services.json

# Log files
*.log

# OS generated files
.DS_Store
.DS_Store?
._*
.Spotlight-V100
.Trashes
Thumbs.db

# Kotlin
*.kotlin_module

# Kotlin/Native
.konan/
*.framework

# CocoaPods
**/Pods/
**/Podfile.lock

# Xcode
*.xcworkspace
*.xcodeproj
xcuserdata/
DerivedData/

# Gradle daemon JVM config
gradle/gradle-daemon-jvm.properties

# Secrets
Secrets.xcconfig

# Claude Code
.claude/
.kotlin/
IGNORE_EOF

# ── 10. Create CI workflow ────────────────────────────────────────────────────

mkdir -p "$TARGET_DIR/.github/workflows"

cat > "$TARGET_DIR/.github/workflows/ci.yml" << 'CI_EOF'
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle.kts', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build
        run: ./gradlew assembleDebug --no-daemon

      - name: Run tests
        run: ./gradlew testDebugUnitTest --no-daemon
CI_EOF

# ── 11. Create Android release workflow ───────────────────────────────────────

cat > "$TARGET_DIR/.github/workflows/release-android.yml" << 'RELEASE_ANDROID_EOF'
name: Release Android → Maven Central

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle.kts', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Validate tag matches gradle version
        run: |
          GRADLE_VERSION=$(grep 'trackflow.version' gradle.properties | cut -d'=' -f2)
          TAG_VERSION="${GITHUB_REF_NAME#v}"
          if [ "$GRADLE_VERSION" != "$TAG_VERSION" ]; then
            echo "ERROR: Tag version ($TAG_VERSION) does not match gradle.properties ($GRADLE_VERSION)"
            exit 1
          fi
          echo "Version validated: $TAG_VERSION"

      - name: Run unit tests
        run: ./gradlew testDebugUnitTest --no-daemon

      - name: Publish to Maven Central
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          GPG_SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}
          GPG_SIGNING_PASSWORD: ${{ secrets.GPG_SIGNING_PASSWORD }}
        run: |
          ./gradlew \
            :trackflow-provider-adobe-analytics:publishMavenPublicationToMavenCentralRepository \
            :trackflow-provider-adobe-edge:publishMavenPublicationToMavenCentralRepository \
            --no-daemon

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          generate_release_notes: true
RELEASE_ANDROID_EOF

# ── 12. Create iOS release workflow ──────────────────────────────────────────

cat > "$TARGET_DIR/.github/workflows/release-ios.yml" << 'RELEASE_IOS_EOF'
name: Release iOS → Private CocoaPods Spec Repo

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: macos-15

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Kotlin/Native compiler
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: konan-${{ runner.os }}-

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-ios-release-${{ hashFiles('**/*.gradle.kts', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: gradle-ios-

      - name: Install CocoaPods
        run: brew install cocoapods

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Extract version from tag
        id: version
        run: |
          TAG_VERSION="${GITHUB_REF_NAME#v}"
          echo "version=$TAG_VERSION" >> "$GITHUB_OUTPUT"
          echo "Releasing version: $TAG_VERSION"

      - name: Validate tag matches gradle version
        run: |
          GRADLE_VERSION=$(grep 'trackflow.version' gradle.properties | cut -d'=' -f2)
          TAG_VERSION="${GITHUB_REF_NAME#v}"
          if [ "$GRADLE_VERSION" != "$TAG_VERSION" ]; then
            echo "ERROR: Tag version ($TAG_VERSION) does not match gradle.properties ($GRADLE_VERSION)"
            exit 1
          fi

      - name: Generate podspecs
        run: ./gradlew podspec --no-daemon

      - name: Build all iOS frameworks (arm64 + simulator)
        run: |
          ./gradlew \
            :trackflow-provider-adobe-analytics-ios:compileKotlinIosArm64 \
            :trackflow-provider-adobe-analytics-ios:compileKotlinIosSimulatorArm64 \
            :trackflow-provider-adobe-edge-ios:compileKotlinIosArm64 \
            :trackflow-provider-adobe-edge-ios:compileKotlinIosSimulatorArm64 \
            --no-daemon

      - name: Build frameworks for distribution
        run: |
          ./gradlew \
            :trackflow-provider-adobe-analytics-ios:syncFramework \
            :trackflow-provider-adobe-edge-ios:syncFramework \
            -Pkotlin.native.cocoapods.platform=iphonesimulator \
            -Pkotlin.native.cocoapods.archs=arm64 \
            -Pkotlin.native.cocoapods.configuration=Release \
            --no-daemon

      - name: Add private spec repo
        env:
          SPEC_REPO_TOKEN: ${{ secrets.SPEC_REPO_TOKEN }}
          COCOAPODS_SPEC_REPO: ${{ secrets.COCOAPODS_SPEC_REPO }}
        run: |
          AUTHED_URL=$(echo "$COCOAPODS_SPEC_REPO" | sed "s|https://|https://${SPEC_REPO_TOKEN}@|")
          pod repo add trackflow-specs "$AUTHED_URL"

      - name: Push podspecs to private spec repo
        run: |
          VERSION=${{ steps.version.outputs.version }}

          SPECS=(
            "trackflow-provider-adobe-analytics-ios/trackflow_provider_adobe_analytics_ios.podspec"
            "trackflow-provider-adobe-edge-ios/trackflow_provider_adobe_edge_ios.podspec"
          )

          for SPEC in "${SPECS[@]}"; do
            echo "Pushing $SPEC..."
            pod repo push trackflow-specs "$SPEC" --allow-warnings --skip-tests
          done

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          generate_release_notes: true
RELEASE_IOS_EOF

# ── 13. Initialize git ───────────────────────────────────────────────────────

cd "$TARGET_DIR"
git init
git add -A
git commit -m "Initial scaffold: Adobe provider modules from TrackFlow"

echo ""
echo "Done! TrackFlow-Pro created at: $TARGET_DIR"
echo ""
echo "Next steps:"
echo "  1. cd $TARGET_DIR"
echo "  2. Review the patched build.gradle.kts files"
echo "  3. Create the GitHub repo and push:"
echo "     git remote add origin git@github.com:lecrane54/TrackFlow-Pro.git"
echo "     git push -u origin main"
echo ""
echo "  4. Delete Adobe modules from the public repo:"
echo "     rm -rf trackflow-provider-adobe-analytics trackflow-provider-adobe-edge"
echo "     rm -rf trackflow-provider-adobe-analytics-ios trackflow-provider-adobe-edge-ios"
