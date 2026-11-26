plugins {
    id("com.android.application")
}

android {
    namespace = "com.ahmed.tvweb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ahmed.tvweb"
        minSdk = 22          // Fire OS 5 (Fire TV Stick 2nd gen) and newer
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK installs without extra setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
