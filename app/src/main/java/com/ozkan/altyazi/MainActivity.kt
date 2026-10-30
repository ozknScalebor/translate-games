package com.ozkan.altyazi

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozkan.altyazi.diagnostics.CrashReporter
import com.ozkan.altyazi.ui.MainScreen
import com.ozkan.altyazi.ui.MainViewModel
import com.ozkan.altyazi.ui.theme.AltyaziTheme

/**
 * AppCompatActivity (rather than ComponentActivity) so AppCompat's per-app language switching
 * works on every supported API level, not only on Android 13+.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReporter.install(applicationContext)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val crashReport = CrashReporter.takeReport(this)
        // A gaming companion must never dim or lock mid-session.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            AltyaziTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(viewModel, crashReport)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
