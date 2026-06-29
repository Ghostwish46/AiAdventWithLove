plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.aichallenge.aiagentapp.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "AiAgentApp"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<JavaExec>("runMcpDemo") {
    group = "application"
    description = "Connect to MCP and list tools"
    mainClass.set("com.aichallenge.aiagentapp.McpDemoMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
