plugins {
    alias(libs.plugins.android.application)
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
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("androidx.navigation:navigation-fragment:2.10.2")
    implementation("androidx.navigation:navigation-ui:2.10.2")
    implementation("com.google.android.material:material:1.14.0")
}