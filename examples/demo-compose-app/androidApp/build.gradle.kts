plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

val javaVersion = libs.versions.javaVersion.get().toInt()

android {
    namespace = "com.jetbrains.example.koog.compose"
    compileSdk = libs.versions.build.android.compileSdk.get().toInt()

    buildFeatures.compose = true

    defaultConfig {
        applicationId = "com.jetbrains.example.koog.compose"
        minSdk = libs.versions.build.android.minSdk.get().toInt()
        targetSdk = libs.versions.build.android.targetSdk.get().toInt()
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
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }
    packaging.resources.excludes.add("META-INF/**")
}

kotlin {
    jvmToolchain(javaVersion)
}

dependencies {
    implementation(project(":commonApp"))
    implementation(libs.androidx.activityCompose)
}
