package com.aichallenge.aiagentapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(navController: NavController) {
    var selected by rememberSaveable { mutableStateOf(ContextStrategy.SLIDING_WINDOW.name) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text("Новый диалог") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Стратегия контекста",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Выбирается один раз при создании диалога и не меняется во время общения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                ContextStrategy.selectable.forEach { strategy ->
                    StrategyOption(
                        strategy = strategy,
                        selected = selected == strategy.name,
                        onSelect = { selected = strategy.name }
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    navController.navigate("chat/new/$selected") {
                        popUpTo("new_chat") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Начать диалог")
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
            "3 слоя памяти: краткосрочная (окно ${SimpleAgent.KEEP_LAST_MESSAGES} реплик), " +
                "рабочая (задача), долговременная (профиль между сессиями)."
    }
    RowSelectable(
        selected = selected,
        onClick = onSelect,
        label = strategy.displayName,
        description = description
    )
}

@Composable
private fun RowSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
