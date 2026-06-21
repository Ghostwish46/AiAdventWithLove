package com.aichallenge.aiagentapp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun platformSafeAreaModifier(): Modifier =
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
