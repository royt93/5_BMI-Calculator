import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

// Load signing credentials trực tiếp từ repo private royt93/myKeyStore (single source of
// truth, không giữ bản copy keystore.jks/keystore.properties trong repo app này nữa).
val keystoreProperties = Properties()
val keystorePropertiesFile = File(
    "/Users/loitran/AndroidStudioProjects/@mckimquyen/myKeyStore/com.samsunggalaxy.bmicalculator/keystore.properties"
)
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.samsunggalaxy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.samsunggalaxy.bmicalculator"
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 20260823
        versionName = "2026.08.23"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppLovin MAX IDs (production)
        buildConfigField(
            "String", "APPLOVIN_SDK_KEY",
            "\"e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt\""
        )
        buildConfigField("String", "APPLOVIN_BANNER_ID",       "\"935687e95c2be5f5\"")
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_ID", "\"e080595a143cf78e\"")
        buildConfigField("String", "APPLOVIN_APP_OPEN_ID",     "\"e349570297a4e092\"")
        buildConfigField("String", "APPLOVIN_REWARDED_ID",     "\"584b6f127bd8534f\"")

        // Privacy Policy — nhúng vào VIP screen footer + consent dialog
        buildConfigField(
            "String", "PRIVACY_POLICY_URL",
            "\"https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f\""
        )
    }

    signingConfigs {
        register("release") {
            storeFile     = file(File(keystorePropertiesFile.parentFile, keystoreProperties["storeFile"] as String))
            storePassword =      keystoreProperties["storePassword"] as String
            keyAlias      =      keystoreProperties["keyAlias"]      as String
            keyPassword   =      keystoreProperties["keyPassword"]   as String
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false") // false = AppLovin MAX
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3940256099942544/9257395921\"")
            // TEST rewarded ID — TODO: thay bằng production ID khi user cấp
            buildConfigField("String", "ADMOB_REWARDED_ID",     "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        getByName("release") {
            //nho check APPLICATION_ID trong manifest
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false") // false = AppLovin MAX
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3612191981543807/9117482667\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3612191981543807/4216509777\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3612191981543807/5066557013\"")
            // TEST rewarded ID — TODO: thay bằng production ID khi user cấp ID thật
            buildConfigField("String", "ADMOB_REWARDED_ID",     "\"ca-app-pub-3940256099942544/5224354917\"")

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
        viewBinding = true
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

    // AdmobApplovinWrapper SDK — AdMob + AppLovin MAX + 7 lớp AdSafety + VIP API
    // Replaces: play-services-ads + applovin mediation
    implementation("com.github.royt93:AdmobApplovinWrapper:1.1.3")

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

    // WorkManager (EPIC-08 T08.1: daily weigh-in reminder, Doze-safe)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")

    // --- Unit tests (src/test — JVM, ./gradlew testDevDebugUnitTest) ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // --- Instrumented/widget/integration tests (src/androidTest — needs device, ./gradlew connectedDevDebugAndroidTest) ---
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
