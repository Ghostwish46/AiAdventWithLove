package com.aichallenge.aiagentapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onProfilesClick: () -> Unit,
    onInvariantsClick: () -> Unit,
    onMcpClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsHubItem(
                title = "Персоны ассистента",
                subtitle = "Стиль и роль ассистента в диалогах",
                onClick = onProfilesClick
            )
            SettingsHubItem(
                title = "Инварианты",
                subtitle = "Предпочтения и запреты по темам (стек, музыка…)",
                onClick = onInvariantsClick
            )
            SettingsHubItem(
                title = "MCP",
                subtitle = "Подключение к MCP-серверу и список доступных инструментов",
                onClick = onMcpClick
            )
        }
    }
}

@Composable
private fun SettingsHubItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
