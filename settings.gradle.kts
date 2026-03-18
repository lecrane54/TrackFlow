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

rootProject.name = "trackflow-android-sdk"
include(":trackflow-core")
include(":trackflow-debug")
include(":testapp")
include(":trackflow-provider-firebase")
include(":trackflow-provider-mixpanel")
include(":trackflow-provider-amplitude")

// iOS provider stubs (compile on macOS only)
include(":trackflow-provider-firebase-ios")
include(":trackflow-provider-mixpanel-ios")
include(":trackflow-provider-amplitude-ios")
include(":trackflow-ios")
