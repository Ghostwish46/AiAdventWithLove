plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aichallenge.aiagentapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aichallenge.aiagentapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val localFile = rootProject.file("local.properties")
        val localLines = if (localFile.exists()) localFile.readLines(Charsets.UTF_8) else emptyList()

        fun readKey(prefix: String, fallback: String = "test-key-replace-me"): String {
            val raw = localLines.mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("$prefix=")) {
                    trimmed.substring(trimmed.indexOf('=') + 1).trim().takeIf { it.isNotEmpty() }
                } else null
            }.firstOrNull() ?: fallback
            return raw.replace("\\", "\\\\").replace("\"", "\\\"")
        }

        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${readKey("DEEPSEEK_API_KEY")}\"")
        buildConfigField("String", "ROUTERAI_API_KEY", "\"${readKey("ROUTERAI_API_KEY")}\"")

    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
