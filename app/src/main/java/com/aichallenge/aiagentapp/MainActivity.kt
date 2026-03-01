package com.aichallenge.aiagentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.createRouterAiApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val api = createRouterAiApi()
        val repository = DeepSeekRepository(api)
        val agentModel = ModelInfo(
            id = "qwen/qwen3.5-flash-02-23",
            label = "Qwen Flash",
            tier = "Быстрая",
            inputPricePerM = 10.0,
            outputPricePerM = 40.0
        )
        val agent = SimpleAgent(
            repository = repository,
            systemPrompt = "Ты полезный AI-ассистент. Отвечай чётко и по делу на русском языке.",
            modelId = agentModel.id
        )
        val viewModel = ChatViewModel(agent, agentModel)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }
}
