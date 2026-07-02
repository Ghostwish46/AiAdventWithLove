plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.aichallenge.mcpserver.MainKt")
}

dependencies {
    implementation(libs.mcp.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

tasks.register<JavaExec>("runMcpServer") {
    group = "application"
    description = "Run AniList MCP server on http://127.0.0.1:3000/mcp"
    mainClass.set("com.aichallenge.mcpserver.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runPlantatorsMcpServer") {
    group = "application"
    description = "Run Plantators MCP server on http://127.0.0.1:3001/mcp"
    mainClass.set("com.aichallenge.mcpserver.PlantatorsMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("plantatorsSmokeTest") {
    group = "verification"
    description = "Smoke-test Plantators API client (public endpoints + guest auth)"
    mainClass.set("com.aichallenge.mcpserver.plantators.PlantatorsSmokeMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
