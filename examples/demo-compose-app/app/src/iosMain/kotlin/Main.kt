import androidx.compose.ui.window.ComposeUIViewController
import com.jetbrains.example.kotlin_agents_demo_app.KoinApp
import platform.UIKit.UIViewController

fun mainViewController(): UIViewController = ComposeUIViewController {
    KoinApp()
}
