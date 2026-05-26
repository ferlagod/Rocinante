plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ferlagod.rocinante"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ferlagod.rocinante"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Splash Screen API (nativa Android 12+, con compat para versiones anteriores)
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Google Fonts para Compose (Lora serif + Inter sans-serif)
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.8")
    // Soporte oficial de idiomas por app (Per-App Language) para APIs < 33
    implementation("androidx.appcompat:appcompat:1.6.1")
    // WorkManager para notificaciones locales
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Jsoup para raspar el HTML de las páginas web
    implementation("org.jsoup:jsoup:1.17.2")
    // Escáner de código de barras (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}