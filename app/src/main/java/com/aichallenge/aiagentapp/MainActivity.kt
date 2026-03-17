package com.aichallenge.aiagentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.ConversationRepository
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.createRouterAiApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val api = createRouterAiApi()
        val deepSeekRepository = DeepSeekRepository(api)
        val conversationRepository = ConversationRepository(applicationContext)
        val modelInfo = ModelInfo(
            id = "openai/gpt-oss-120b",
            label = "GPT-OSS 120B",
            tier = "Сильная",
            inputPricePerM = 5.0,
            outputPricePerM = 26.0,
            contextLength = 131_000
        )
        val systemPrompt = "Ты полезный AI-ассистент. Отвечай чётко и по делу на русском языке."

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "chat/new"
                    ) {
                        composable("home") {
                            val homeViewModel: HomeViewModel = viewModel {
                                HomeViewModel(conversationRepository)
                            }
                            HomeScreen(
                                viewModel = homeViewModel,
                                navController = navController
                            )
                        }
                        composable(
                            route = "chat/{conversationId}",
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: "new"
                            val chatViewModel: ChatViewModel = viewModel(
                                key = conversationId,
                                factory = object : ViewModelProvider.Factory {
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        val conv = if (conversationId == "new") null else conversationRepository.getById(conversationId)
                                        val savedMessages = conv?.messages ?: emptyList()
                                        val initialHistory = savedMessages.map { it.toChatMessage() }
                                        val agent = SimpleAgent(
                                            repository = deepSeekRepository,
                                            modelInfo = modelInfo,
                                            systemPrompt = systemPrompt,
                                            initialHistory = initialHistory
                                        )
                                        val initialMessages = savedMessages.map { sm ->
                                            UiMessage(
                                                role = sm.role,
                                                content = sm.content,
                                                usage = sm.toUsage(),
                                                elapsedMs = sm.elapsedMs ?: 0,
                                                estimatedCostRub = sm.estimatedCostRub
                                            )
                                        }
                                        @Suppress("UNCHECKED_CAST")
                                        return ChatViewModel(
                                            agent = agent,
                                            conversationId = if (conversationId == "new") null else conversationId,
                                            conversationRepository = conversationRepository,
                                            initialMessages = initialMessages,
                                            contextLength = modelInfo.contextLength
                                        ) as T
                                    }
                                }
                            )
                            ChatScreen(
                                viewModel = chatViewModel,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
