package com.jetbrains.example.koog.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jetbrains.example.koog.compose.local.AndroidLocalLLMClient
import com.jetbrains.example.koog.compose.local.AndroidLocalLLMClientConfig
import com.jetbrains.example.koog.compose.local.FunctionGemma
import com.jetbrains.example.koog.compose.local.Gemma
import com.jetbrains.example.koog.compose.local.Gemma3

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinApp(
                client = AndroidLocalLLMClient(
                    AndroidLocalLLMClientConfig()
                ),
                model = FunctionGemma
            )
        }
    }
}
