plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.browserengine"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // browser-engine depends on all the sub-engines
    implementation(project(":tab-engine"))
    implementation(project(":search-engine"))
    implementation(project(":download-engine"))
    implementation(project(":bookmark-engine"))
    implementation(project(":history-engine"))
    implementation(project(":translate-engine"))
    implementation(project(":reader-engine"))
    implementation(project(":ai-engine"))
    implementation(project(":news-engine"))
    implementation(project(":adblock-engine"))
    implementation(project(":media-engine"))
    implementation(project(":security-engine"))
    implementation(project(":settings-engine"))
    implementation(project(":permission-engine"))
    implementation(project(":notification-engine"))
    implementation(project(":backup-engine"))
}
