package com.aichallenge.aiagentapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.parseBranchingUiSnapshot
import com.aichallenge.aiagentapp.data.parseStickyFactsJson
import com.aichallenge.aiagentapp.data.parseWorkingMemoryJson
import kotlinx.coroutines.launch

@Composable
fun App(deps: AppDependencies) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onCopied: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar("Скопировано") }
    }

    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { contentPadding ->
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        val homeViewModel: HomeViewModel = viewModel {
                            HomeViewModel(deps.conversationRepository)
                        }
                        HomeScreen(
                            viewModel = homeViewModel,
                            navController = navController
                        )
                    }
                    composable("new_chat") {
                        NewChatScreen(navController = navController)
                    }
                    composable(
                        route = "chat/new/{strategy}",
                        arguments = listOf(
                            navArgument("strategy") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val strategyName = backStackEntry.arguments
                            ?.getString("strategy")
                            ?: ContextStrategy.SLIDING_WINDOW.name
                        val strategy = ContextStrategy.fromSavedName(strategyName)
                        val chatViewModel: ChatViewModel = viewModel(key = "new_$strategyName") {
                            val agent = SimpleAgent(
                                repository = deps.deepSeekRepository,
                                modelInfo = deps.modelInfo,
                                systemPrompt = deps.systemPrompt,
                                initialContextStrategy = strategy,
                                longTermMemoryStore = if (strategy == ContextStrategy.MEMORY_LAYERS) {
                                    deps.longTermMemoryRepository
                                } else {
                                    null
                                }
                            )
                            ChatViewModel(
                                agent = agent,
                                conversationId = null,
                                conversationRepository = deps.conversationRepository,
                                contextLength = deps.modelInfo.contextLength
                            )
                        }
                        ChatScreen(
                            viewModel = chatViewModel,
                            navController = navController,
                            onCopied = onCopied
                        )
                    }
                    composable(
                        route = "chat/{conversationId}",
                        arguments = listOf(
                            navArgument("conversationId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                        val chatViewModel: ChatViewModel = viewModel(key = conversationId) {
                            val conv = deps.conversationRepository.getById(conversationId)
                            val savedMessages = conv?.messages ?: emptyList()
                            val savedStrategy = ContextStrategy.fromSavedName(conv?.contextStrategy)
                            val initialBranching =
                                if (savedStrategy == ContextStrategy.BRANCHING) {
                                    parseBranchingUiSnapshot(conv?.branchingJson)
                                } else {
                                    null
                                }
                            val initialHistory = when {
                                initialBranching != null -> emptyList()
                                else -> savedMessages.map { it.toChatMessage() }
                            }
                            val initialFacts =
                                if (savedStrategy == ContextStrategy.FACTS_KV) {
                                    parseStickyFactsJson(conv?.stickyFactsJson)
                                } else {
                                    emptyMap()
                                }
                            val agentBranching =
                                if (savedStrategy == ContextStrategy.BRANCHING) {
                                    com.aichallenge.aiagentapp.data.parseBranchingSnapshot(conv?.branchingJson)
                                } else {
                                    null
                                }
                            val initialWorking =
                                if (savedStrategy == ContextStrategy.MEMORY_LAYERS) {
                                    parseWorkingMemoryJson(conv?.workingMemoryJson)
                                } else {
                                    com.aichallenge.aiagentapp.agent.memory.WorkingMemory()
                                }
                            val agent = SimpleAgent(
                                repository = deps.deepSeekRepository,
                                modelInfo = deps.modelInfo,
                                systemPrompt = deps.systemPrompt,
                                initialHistory = initialHistory,
                                initialContextStrategy = savedStrategy,
                                initialStickyFacts = initialFacts,
                                initialBranching = agentBranching,
                                initialWorkingMemory = initialWorking,
                                longTermMemoryStore = if (savedStrategy == ContextStrategy.MEMORY_LAYERS) {
                                    deps.longTermMemoryRepository
                                } else {
                                    null
                                }
                            )
                            val initialMessages = if (initialBranching != null) {
                                emptyList()
                            } else {
                                savedMessages.map { sm ->
                                    UiMessage(
                                        role = sm.role,
                                        content = sm.content,
                                        usage = sm.toUsage(),
                                        elapsedMs = sm.elapsedMs ?: 0,
                                        estimatedCostRub = sm.estimatedCostRub
                                    )
                                }
                            }
                            ChatViewModel(
                                agent = agent,
                                conversationId = conversationId,
                                conversationRepository = deps.conversationRepository,
                                initialMessages = initialMessages,
                                initialBranching = initialBranching,
                                contextLength = deps.modelInfo.contextLength
                            )
                        }
                        ChatScreen(
                            viewModel = chatViewModel,
                            navController = navController,
                            onCopied = onCopied
                        )
                    }
                }
            }
        }
    }
}
