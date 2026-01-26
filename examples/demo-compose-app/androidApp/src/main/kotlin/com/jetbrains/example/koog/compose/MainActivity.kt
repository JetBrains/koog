package com.jetbrains.example.koog.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jetbrains.example.koog.compose.local.AndroidLLocalLLMClient
import com.jetbrains.example.koog.compose.local.AndroidLocalModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinApp(
                client = AndroidLLocalLLMClient(
                    this,
                    "/data/local/tmp/llm"
                ),
                model = AndroidLocalModels.Chat.Gemma
            )
        }
    }
}
