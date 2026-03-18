plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        version = "1.0.0"
        summary = "TrackFlow iOS SDK — unified framework with all providers"
        homepage = "https://github.com/lecrane54/TrackFlow"
        ios.deploymentTarget = "15.0"

        framework {
            baseName = "TrackFlow"
            isStatic = false
            linkerOpts("-undefined", "dynamic_lookup")

            // Re-export so all types are visible in Swift through this single framework
            export(project(":trackflow-core"))
            export(project(":trackflow-provider-firebase-ios"))
            export(project(":trackflow-provider-amplitude-ios"))
            export(project(":trackflow-provider-mixpanel-ios"))
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":trackflow-core"))
                api(project(":trackflow-provider-firebase-ios"))
                api(project(":trackflow-provider-amplitude-ios"))
                api(project(":trackflow-provider-mixpanel-ios"))
            }
        }
    }
}
