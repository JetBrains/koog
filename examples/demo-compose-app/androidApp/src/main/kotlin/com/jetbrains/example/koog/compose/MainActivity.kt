package com.jetbrains.example.koog.compose

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jetbrains.example.kotlin_agents_demo_app.local.AndroidLLocalLLMClient
import com.jetbrains.example.kotlin_agents_demo_app.local.AndroidLocalLLMProvider
import com.jetbrains.example.kotlin_agents_demo_app.local.AndroidLocalModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinApp(
                executor = MultiLLMPromptExecutor(
                    AndroidLocalLLMProvider to AndroidLLocalLLMClient(
                        this,
                        "/data/local/tmp/llm"
                    )
                ),
                model = AndroidLocalModels.Chat.Gemma
            )
        }
    }
}
