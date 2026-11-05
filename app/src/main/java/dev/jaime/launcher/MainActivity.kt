package dev.jaime.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jaime.launcher.ui.LauncherFont
import dev.jaime.launcher.ui.LauncherScreen
import dev.jaime.launcher.ui.LauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    // The role dialog must be started "for result" or it can't tell who is asking.
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val font by viewModel.prefs.font.collectAsStateWithLifecycle()
            LauncherTheme(LauncherFont.fromId(font)) {
                LauncherScreen(
                    viewModel = viewModel,
                    isDefaultLauncher = ::isDefaultLauncher,
                    onRequestDefaultLauncher = ::requestDefaultLauncher,
                )
            }
        }

        // Status bar hidden or shown, per launcher settings, and kept that way as the setting changes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.prefs.hideStatusBar.collect { hidden -> applyStatusBar(hidden) }
            }
        }

        // Offer to become the Home app once, on first run.
        if (savedInstanceState == null && !viewModel.prefs.askedForHomeRole && !isDefaultLauncher()) {
            viewModel.prefs.askedForHomeRole = true
            requestDefaultLauncher()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Home while the launcher is showing: jump back to the home page. (Coming back from
            // another app, it was already reset when it stopped.)
            viewModel.onHomePressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Some Android versions bring the bar back after a dialog or the shade; hide it again.
        if (hasFocus) applyStatusBar(viewModel.prefs.hideStatusBar.value)
    }

    /**
     * Hides or shows the status bar. While hidden, a swipe down from the top edge shows it for a
     * few seconds, over the launcher (it leaves the layout alone), the way full-screen apps do.
     */
    private fun applyStatusBar(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun isDefaultLauncher(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == packageName
        }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        SystemActions.openHomeSettings(this)
    }
}
