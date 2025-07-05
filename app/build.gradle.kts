plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.moment"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.moment"
        minSdk = 26 // Bluetooth LE scanning and advertising APIs are more stable from API 21+, 26 for more modern features
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // ViewModel and LiveData for observing data
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // RecyclerView for displaying lists
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room for local database (we might use it later for profile)
    // implementation("androidx.room:room-runtime:2.6.1")
    // annotationProcessor("androidx.room:room-compiler:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1") // Kotlin Extensions and Coroutines support for Room

    // For SharedPreferences
    implementation("androidx.preference:preference-ktx:1.2.1")
}
