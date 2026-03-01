package com.aichallenge.aiagentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aichallenge.aiagentapp.data.createRouterAiApi
import com.aichallenge.aiagentapp.data.DeepSeekRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val routerAiApi = createRouterAiApi()
        val repository = DeepSeekRepository(routerAiApi)
        val viewModel = ChatViewModel(repository)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }
}
