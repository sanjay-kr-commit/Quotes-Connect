package connects.quotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import connects.quotes.config.PlatformServerEndpointStore
import connects.quotes.config.ServerEndpointManager
import connects.quotes.crypto.PlatformEncryptedKeystoreStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformServerEndpointStore.init(applicationContext)
        ServerEndpointManager.reloadPersisted()
        connects.quotes.client.PlatformSessionStore.init(applicationContext)
        PlatformEncryptedKeystoreStore.init(applicationContext)
        connects.quotes.database.ClientChatDatabase.init(applicationContext)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}