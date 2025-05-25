plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.samsunggalaxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samsunggalaxy.bmicalculator"
        minSdk = 23
        //noinspection EditedTargetSdkVersion
        targetSdk = 35
        versionCode = 20250525
        versionName = "2025.05.25"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
        }
        getByName("release") {
            //nho check APPLICATION_ID trong manifest
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3612191981543807/9117482667\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3612191981543807/4216509777\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-3612191981543807/5066557013\"")

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
            //            buildConfigField("String", "FLAVOR_buildEnv", "dev")

            resValue("string", "app_name", "BMI Calculator 2025 DEV")

//            resValue(
//                "string",
//                "SDK_KEY",
//                "e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt"
//            )
//            resValue("string", "BANNER", "935687e95c2be5f5")
//            resValue("string", "INTER", "e080595a143cf78e")
//            resValue("string", "EnableAdInter", "false")
//            resValue("string", "EnableAdBanner", "true")
        }
        create("production") {
            dimension = "type"
            //            buildConfigField("String", "FLAVOR_buildEnv", "prod")

            resValue("string", "app_name", "BMI Calculator 2025")

//            resValue(
//                "string",
//                "SDK_KEY",
//                "e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt"
//            )
//            resValue("string", "BANNER", "935687e95c2be5f5")
//            resValue("string", "INTER", "e080595a143cf78e")
//            resValue("string", "EnableAdInter", "true")
//            resValue("string", "EnableAdBanner", "true")
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
    android.buildFeatures.dataBinding = true
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
//    implementation("com.applovin:applovin-sdk:13.1.0")
    implementation("com.google.android.gms:play-services-ads:24.3.0")
//    implementation("com.google.ads.mediation:applovin:13.2.0.1")
    //for testing
//    testImplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test.ext:junit:1.1.5")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
}
