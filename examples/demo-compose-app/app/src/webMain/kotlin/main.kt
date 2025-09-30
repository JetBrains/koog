import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.jetbrains.example.kotlin_agents_demo_app.KoinApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport { KoinApp() }
