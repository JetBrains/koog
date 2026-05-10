package com.jetbrains.example.koog.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jetbrains.example.koog.compose.local.LiteRTLLMClient
import com.jetbrains.example.koog.compose.local.LiteRTClientConfig
import com.jetbrains.example.koog.compose.local.LiteRTLLModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinApp(
                client = LiteRTLLMClient(
                    LiteRTClientConfig()
                ),
                model = LiteRTLLModels.FunctionGemma
            )
        }
    }
}
