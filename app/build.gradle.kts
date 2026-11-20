plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.gymrats"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gymrats"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    // 기본 Android 라이브러리
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 동영상 재생 기능
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")


    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")


    // Glide - 이미지 표시
    implementation("com.github.bumptech.glide:glide:4.16.0")


    // Navigation
    implementation("androidx.navigation:navigation-fragment:2.10.2")
    implementation("androidx.navigation:navigation-ui:2.10.2")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Media3 - 동영상 재생
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
