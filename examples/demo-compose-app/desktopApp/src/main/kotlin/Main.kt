import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jetbrains.example.koog.compose.KoinApp
import java.awt.Dimension

fun main() = application {
    Window(
        title = "Koog Demo App",
        state = rememberWindowState(width = 800.dp, height = 600.dp),
        onCloseRequest = ::exitApplication,
    ) {
        window.minimumSize = Dimension(350, 600)
        KoinApp()
    }
}
