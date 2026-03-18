plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.trackflow.provider.adobe.analytics"
    compileSdk = (findProperty("android.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":trackflow-core"))

    implementation(platform("com.adobe.marketing.mobile:sdk-bom:3.17.0"))
    implementation("com.adobe.marketing.mobile:core")
    implementation("com.adobe.marketing.mobile:analytics")
    implementation("com.adobe.marketing.mobile:edgebridge")
    implementation("com.adobe.marketing.mobile:identity")
    implementation("com.adobe.marketing.mobile:lifecycle")
    implementation("com.adobe.marketing.mobile:userprofile")
    implementation("com.adobe.marketing.mobile:signal")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
}
