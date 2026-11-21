import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("androidx.activity:activity-compose:1.11.0")
        }
    }
}

android {
    namespace = "pl.tuneprint"
    compileSdk = 36

    sourceSets["main"].assets.srcDir(rootProject.file("songs"))

    defaultConfig {
        val publicHost = providers.gradleProperty("tuneprintPublicHost").orNull
        if (publicHost != null) {
            require(Regex("[A-Za-z0-9.-]+").matches(publicHost)) { "Expected a public hostname" }
        }
        resValue("string", "default_server_address",
            publicHost?.let { "wss://$it/game" } ?: "ws://10.0.2.2:8080/game")
        applicationId = "pl.tuneprint"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
