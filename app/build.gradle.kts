plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "0.16.0"
val appVersionParts = appVersionName.split('.').map(String::toInt)
require(appVersionParts.size == 3 && appVersionParts.all { it in 0..999 }) {
    "App version must use MAJOR.MINOR.PATCH with components from 0 to 999."
}
val appVersionCode =
    appVersionParts[0] * 1_000_000 + appVersionParts[1] * 1_000 + appVersionParts[2]
require(appVersionCode > 0) { "App version must be greater than 0.0.0." }

val marketDataBaseUrl = providers
    .environmentVariable("MNM_MARKET_DATA_BASE_URL")
    .orElse("")
    .get()

val buildCommitSha = providers
    .environmentVariable("MNM_BUILD_COMMIT_SHA")
    .orElse("local")
    .get()

val releaseKeystorePath = providers.environmentVariable("MNM_ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers
    .environmentVariable("MNM_ANDROID_KEYSTORE_PASSWORD")
    .orNull
val releaseKeyAlias = providers.environmentVariable("MNM_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MNM_ANDROID_KEY_PASSWORD").orNull
val managedReleaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val partialReleaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).any { !it.isNullOrBlank() } && !managedReleaseSigningConfigured

require(!partialReleaseSigningConfigured) {
    "Release signing requires the keystore path, passwords, and key alias together."
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.mnm.auseekers"
    compileSdk = 35

    signingConfigs {
        if (managedReleaseSigningConfigured) {
            create("managedRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.mnm.auseekers"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "MARKET_DATA_BASE_URL",
            marketDataBaseUrl.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "BUILD_COMMIT_SHA",
            buildCommitSha.asBuildConfigString(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (managedReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("managedRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.code.gson:gson:2.14.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
