package com.aichallenge.aiagentapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.invariant.InvariantPromptBuilder
import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.invariant.InvariantRule
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvariantBlockEditorScreen(
    viewModel: InvariantsViewModel,
    navController: NavController,
    blockId: String?,
    editorKey: String
) {
    var title by remember(editorKey) { mutableStateOf("") }
    var domainHint by remember(editorKey) { mutableStateOf("") }
    var enabled by remember(editorKey) { mutableStateOf(true) }
    val rules = remember(editorKey) { mutableStateListOf<InvariantRule>() }

    LaunchedEffect(editorKey, blockId) {
        if (blockId == null) {
            title = ""
            domainHint = ""
            enabled = true
            rules.clear()
            rules.add(InvariantRule(viewModel.newRuleId(), ""))
        } else {
            val block = viewModel.getBlock(blockId)
            if (block != null) {
                title = block.title
                domainHint = block.domainHint
                enabled = block.enabled
                rules.clear()
                rules.addAll(block.rules)
            }
        }
    }

    val previewBlock = InvariantBlock(
        id = blockId ?: "preview",
        title = title.ifBlank { "Новый блок" },
        domainHint = domainHint,
        rules = rules.filter { it.text.isNotBlank() },
        enabled = enabled
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text(if (blockId == null) "Новый блок" else "Редактировать блок") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название блока") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = domainHint,
                onValueChange = { domainHint = it },
                label = { Text("Подсказка домена (android, kotlin, музыка)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Активен", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Правила", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Обязательно: Kotlin, Compose. Запрет: «Не использовать Java». Все правила — строгие, не рекомендации.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            rules.forEachIndexed { index, rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = rule.text,
                        onValueChange = { newText ->
                            rules[index] = rule.copy(text = newText)
                        },
                        label = { Text("Правило ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = { rules.removeAt(index) },
                        enabled = rules.size > 1
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить правило")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            OutlinedButton(
                onClick = { rules.add(InvariantRule(viewModel.newRuleId(), "")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Добавить правило")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Превью промпта", style = MaterialTheme.typography.labelLarge)
            Text(
                text = InvariantPromptBuilder.buildInvariantsBlock(listOf(previewBlock)).ifBlank { "(пусто)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.saveBlock(
                            id = blockId,
                            title = title,
                            domainHint = domainHint,
                            rules = rules.toList(),
                            enabled = enabled
                        )
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Text("Сохранить")
            }
            if (blockId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.deleteBlock(blockId)
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить блок", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
