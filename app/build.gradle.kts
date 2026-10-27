plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.rebuild"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rebuild"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "0.1.17"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Every Rebuild APK is signed with the same key, so each one installs over the last and keeps its data. Locally
    // that is this machine's debug key; CI is given the same key through secrets (REBUILD_KEYSTORE_FILE and its
    // passwords) and the release workflow refuses to publish an APK signed with anything else.
    val sharedKeystore = System.getenv("REBUILD_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (sharedKeystore != null) create("shared") {
            storeFile = file(sharedKeystore)
            storePassword = System.getenv("REBUILD_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("REBUILD_KEY_ALIAS")
            keyPassword = System.getenv("REBUILD_KEY_PASSWORD")
        }
    }
    buildTypes {
        // What goes on the phone: R8-optimized and not debuggable, which is where Compose runs at full speed.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (sharedKeystore != null) "shared" else "debug")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.16.0")
    // Installs the baseline profiles Compose and our own code ship with, so screens are compiled ahead of first use.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
