package com.aichallenge.aiagentapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.ui.ProfileAvatar
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navController: NavController
) {
    val customProfiles by viewModel.customProfiles.collectAsState()

    LaunchedEffect(navController.currentBackStackEntry?.destination?.route) {
        if (navController.currentBackStackEntry?.destination?.route == "settings") {
            viewModel.reload()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text("Настройки профилей") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("settings/profile/new") }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить профиль")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Кастомные профили",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Создайте свои персоны ассистента. Они появятся при создании нового диалога.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (customProfiles.isEmpty()) {
                Text(
                    text = "Пока нет кастомных профилей. Нажмите + чтобы добавить.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(customProfiles, key = { it.id }) { profile ->
                        CustomProfileItem(
                            profile = profile,
                            onClick = { navController.navigate("settings/profile/${profile.id}") },
                            onDelete = { viewModel.deleteProfile(profile.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomProfileItem(
    profile: AssistantProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(profile = profile, size = 48.dp)
            Spacer(modifier = Modifier.padding(horizontal = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.label,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (profile.communicationStyle.isNotBlank()) {
                    Text(
                        text = profile.communicationStyle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
