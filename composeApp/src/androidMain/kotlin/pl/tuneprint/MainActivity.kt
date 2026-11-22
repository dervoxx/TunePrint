package pl.tuneprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var online: OnlineGame

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        online = ViewModelProvider(this)[OnlineGame::class.java]
        setContent { OnlineScreens(online) }
    }

    override fun onResume() {
        super.onResume()
        online.resume()
    }

    override fun onPause() {
        online.pause()
        super.onPause()
    }
}
