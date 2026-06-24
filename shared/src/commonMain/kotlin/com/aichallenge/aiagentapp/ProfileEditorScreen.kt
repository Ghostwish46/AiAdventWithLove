package com.aichallenge.aiagentapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfilePromptBuilder
import com.aichallenge.aiagentapp.ui.ProfileAvatar
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: SettingsViewModel,
    navController: NavController,
    profileId: String?
) {
    val existing = profileId?.let { viewModel.getProfile(it) }
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var personaDescription by rememberSaveable { mutableStateOf(existing?.personaDescription ?: "") }
    var communicationStyle by rememberSaveable { mutableStateOf(existing?.communicationStyle ?: "") }
    var responseFormat by rememberSaveable { mutableStateOf(existing?.responseFormat ?: "") }
    var constraints by rememberSaveable { mutableStateOf(existing?.constraints ?: "") }

    val previewProfile = AssistantProfile(
        id = profileId ?: "preview",
        label = label.ifBlank { "Новый профиль" },
        personaDescription = personaDescription,
        communicationStyle = communicationStyle,
        responseFormat = responseFormat,
        constraints = constraints
    )

    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.reload()
            viewModel.getProfile(profileId)?.let { profile ->
                label = profile.label
                personaDescription = profile.personaDescription
                communicationStyle = profile.communicationStyle
                responseFormat = profile.responseFormat
                constraints = profile.constraints
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text(if (profileId == null) "Новый профиль" else "Редактировать") },
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
            ProfileAvatar(
                profile = previewProfile,
                size = 72.dp,
                showLabel = true,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Имя") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = personaDescription,
                onValueChange = { personaDescription = it },
                label = { Text("Описание роли") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = communicationStyle,
                onValueChange = { communicationStyle = it },
                label = { Text("Стиль общения") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = responseFormat,
                onValueChange = { responseFormat = it },
                label = { Text("Формат ответов") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = constraints,
                onValueChange = { constraints = it },
                label = { Text("Ограничения") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Превью промпта",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = ProfilePromptBuilder.previewBlock(previewProfile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (label.isNotBlank()) {
                        viewModel.saveProfile(
                            id = profileId,
                            label = label,
                            communicationStyle = communicationStyle,
                            responseFormat = responseFormat,
                            constraints = constraints,
                            personaDescription = personaDescription
                        )
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = label.isNotBlank()
            ) {
                Text("Сохранить")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
