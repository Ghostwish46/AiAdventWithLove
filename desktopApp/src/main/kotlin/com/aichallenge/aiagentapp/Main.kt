package com.aichallenge.aiagentapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val deps = createAppDependencies()
    Window(
        onCloseRequest = ::exitApplication,
        title = "AI Agent"
    ) {
        App(deps)
    }
}
