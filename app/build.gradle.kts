plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.example.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.swiftbrowser.web"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Submodule dependencies
    implementation(project(":browser-engine"))
    implementation(project(":extension-engine"))
    implementation(project(":permission-engine"))
    implementation(project(":adblock-engine"))
    implementation(project(":tab-engine"))
    implementation(project(":search-engine"))
    implementation(project(":translate-engine"))
    implementation(project(":reader-engine"))
    implementation(project(":bookmark-engine"))
    implementation(project(":history-engine"))
    implementation(project(":download-engine"))
    implementation(project(":ai-engine"))
    implementation(project(":news-engine"))
    implementation(project(":media-engine"))
    implementation(project(":security-engine"))
    implementation(project(":settings-engine"))
    implementation(project(":notification-engine"))
    implementation(project(":backup-engine"))
    implementation(project(":database-core"))
    implementation(project(":network-core"))
    implementation(project(":analytics-core"))
    implementation(project(":ui-core"))
    implementation(project(":image-engine"))
    implementation(project(":video-engine"))
    implementation(project(":audio-engine"))
    implementation(project(":developer-tools-engine"))
    implementation(project(":media-notification-engine"))
    implementation(project(":media-detector-engine"))
    implementation(project(":download-ui-engine"))
    implementation(project(":download-notification-engine"))

    // Direct library dependencies
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
}
