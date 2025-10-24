import androidx.compose.ui.window.ComposeUIViewController
import com.jetbrains.example.koog.compose.KoinApp
import platform.UIKit.UIViewController

fun mainViewController(): UIViewController = ComposeUIViewController {
    KoinApp()
}
