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
        summary = "TrackFlow Amplitude iOS Provider"
        homepage = "https://github.com/lecrane54/TrackFlow"
        ios.deploymentTarget = "15.0"

        framework {
            isStatic = false
            linkerOpts("-undefined", "dynamic_lookup")
        }

        pod("Amplitude", "~> 8.0")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":trackflow-core"))
            }
        }
    }
}
