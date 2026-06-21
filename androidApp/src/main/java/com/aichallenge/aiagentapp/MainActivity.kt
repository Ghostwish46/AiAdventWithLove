package com.aichallenge.aiagentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aichallenge.aiagentapp.platform.ApiKeys
import com.aichallenge.aiagentapp.platform.initAndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initAndroidPlatform(
            context = this,
            apiKeys = ApiKeys(
                routerAiApiKey = BuildConfig.ROUTERAI_API_KEY,
                deepSeekApiKey = BuildConfig.DEEPSEEK_API_KEY
            )
        )
        val deps = createAppDependencies()

        setContent {
            App(deps)
        }
    }
}
