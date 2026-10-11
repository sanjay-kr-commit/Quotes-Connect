package connects.quotes

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    var zoomFactor by remember { mutableStateOf(1.0f) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Quotes Connect",
        icon = painterResource("icon.png"),
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                when (event.key) {
                    Key.Plus, Key.Equals, Key.NumPadAdd -> {
                        zoomFactor = (zoomFactor + 0.1f).coerceIn(0.6f, 2.2f)
                        true
                    }
                    Key.Minus, Key.NumPadSubtract -> {
                        zoomFactor = (zoomFactor - 0.1f).coerceIn(0.6f, 2.2f)
                        true
                    }
                    Key.Zero, Key.NumPad0 -> {
                        zoomFactor = 1.0f
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        val originalDensity = LocalDensity.current
        val scaledDensity = remember(originalDensity, zoomFactor) {
            Density(
                density = originalDensity.density * zoomFactor,
                fontScale = originalDensity.fontScale * zoomFactor
            )
        }
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            App()
        }
    }
}