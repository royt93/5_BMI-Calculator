plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.samsunggalaxy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samsunggalaxy.bmicalculator"
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = 20260511
        versionName = "2026.05.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppLovin MAX IDs (production)
        buildConfigField(
            "String", "APPLOVIN_SDK_KEY",
            "\"e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt\""
        )
        buildConfigField("String", "APPLOVIN_BANNER_ID",       "\"935687e95c2be5f5\"")
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_ID", "\"e080595a143cf78e\"")
        buildConfigField("String", "APPLOVIN_APP_OPEN_ID",     "\"e349570297a4e092\"")
        buildConfigField("String", "APPLOVIN_REWARD_ID",       "\"584b6f127bd8534f\"")
    }

    signingConfigs {
        register("release") {
            storeFile = file("keystores.jks")
            storePassword = "27072000"
            keyAlias = "mckimquyen"
            keyPassword = "27072000"
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false") // false = AppLovin MAX
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3940256099942544/9257395921\"")
        }
        getByName("release") {
            //nho check APPLICATION_ID trong manifest
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false") // false = AppLovin MAX
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3612191981543807/9117482667\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3612191981543807/4216509777\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3612191981543807/5066557013\"")

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    buildTypes.all { isCrunchPngs = false }

    flavorDimensions.add("type")

    productFlavors {
        create("dev") {
            dimension = "type"
            resValue("string", "app_name", "BMI Calculator 2026 DEV")
        }
        create("production") {
            dimension = "type"
            resValue("string", "app_name", "BMI Calculator 2026")
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    //noinspection DataBindingWithoutKapt
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    // Disable lint rule that crashes with Kotlin 2.0.20 (IncompatibleClassChangeError)
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.github.adityagohad:HorizontalPicker:1.0.1")
    implementation("com.github.psuzn:WheelView:1.0.0")
    implementation("com.github.CNCoderX:WheelView:1.2.6")
    implementation("com.github.mhdmoh:swipe-button:1.0.3")

    // AdmobWrapper SDK — kéo toàn bộ AdMob + AppLovin MAX + 7 lớp AdSafety
    // Replaces: play-services-ads + applovin mediation
    implementation("com.github.royt93:AdmobWrapper:1.1.1")

    // Room database
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // MPAndroidChart for graphs
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Navigation Component
    val navVersion = "2.8.5"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
