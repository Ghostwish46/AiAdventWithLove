plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
}
