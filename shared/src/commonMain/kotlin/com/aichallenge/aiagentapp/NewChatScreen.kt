package com.aichallenge.aiagentapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfileCatalog
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.ui.ProfileAvatar
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    navController: NavController,
    profileCatalog: ProfileCatalog,
    profileCatalogStore: com.aichallenge.aiagentapp.data.ProfileCatalogStore
) {
    var selectedStrategy by rememberSaveable { mutableStateOf(ContextStrategy.SLIDING_WINDOW.name) }
    var selectedProfileId by rememberSaveable { mutableStateOf(AssistantProfile.ID_NEUTRAL) }
    var selectableProfiles by remember { mutableStateOf(profileCatalog.allSelectableProfiles()) }

    LaunchedEffect(navController.currentBackStackEntry?.destination?.route) {
        if (navController.currentBackStackEntry?.destination?.route == "new_chat") {
            profileCatalog.updateCustomProfiles(profileCatalogStore.loadCustomProfiles())
            selectableProfiles = profileCatalog.allSelectableProfiles()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text("Новый диалог") },
                navigationIcon = {
                    IconButton(onClick = { navigateBackToHome(navController) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    navController.navigate("chat/new/$selectedStrategy/$selectedProfileId") {
                        popUpTo("new_chat") { inclusive = true }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text("Начать диалог")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Стратегия контекста",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Выбирается один раз при создании диалога.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.selectableGroup()) {
                ContextStrategy.selectable.forEach { strategy ->
                    StrategyOption(
                        strategy = strategy,
                        selected = selectedStrategy == strategy.name,
                        onSelect = { selectedStrategy = strategy.name }
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Персона ассистента",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Ассистент будет отвечать в выбранной роли с соответствующим аватаром.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.selectableGroup()) {
                selectableProfiles.forEach { profile ->
                    ProfileOption(
                        profile = profile,
                        selected = selectedProfileId == profile.id,
                        onSelect = { selectedProfileId = profile.id }
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun navigateBackToHome(navController: NavController) {
    navController.navigate("home") {
        popUpTo("home") { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun ProfileOption(
    profile: AssistantProfile,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val description = profile.personaDescription.ifBlank {
        when (profile.id) {
            AssistantProfile.ID_NEUTRAL -> "Стандартный ассистент без роли"
            else -> profile.communicationStyle
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        ProfileAvatar(profile = profile, size = 36.dp, modifier = Modifier.padding(horizontal = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.label, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StrategyOption(
    strategy: ContextStrategy,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val description = when (strategy) {
        ContextStrategy.SLIDING_WINDOW ->
            "В промпт попадают только последние ${SimpleAgent.KEEP_LAST_MESSAGES} сообщений."
        ContextStrategy.FACTS_KV ->
            "Блок фактов (ключ–значение) + последние ${SimpleAgent.KEEP_LAST_MESSAGES} сообщений."
        ContextStrategy.BRANCHING ->
            "Checkpoint и две независимые ветки A/B от точки разветвления."
        ContextStrategy.MEMORY_LAYERS ->
            "3 слоя памяти: краткосрочная, рабочая, долговременная."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = strategy.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
