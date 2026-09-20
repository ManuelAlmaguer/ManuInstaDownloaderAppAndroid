package com.manu.reeldrop.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.ui.navigation.ReelDropRoot
import com.manu.reeldrop.ui.theme.ReelDropTheme
import com.manu.reeldrop.util.Permissions
import com.manu.reeldrop.util.UrlUtils

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)
    private var openQueue by mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Nothing else to do: the Settings screen shows the resulting state. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceLocator.init(applicationContext)

        setContent {
            val settings by ServiceLocator.settings.flow.collectAsStateWithLifecycle(
                initialValue = ServiceLocator.settings.cached,
            )
            ReelDropTheme(
                theme = settings.theme,
                mode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                ReelDropRoot(
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = { sharedUrl = null },
                    openQueue = openQueue,
                    onQueueOpened = { openQueue = false },
                )
            }
        }

        handleIntent(intent)
        askInitialPermissionsIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = UrlUtils.extractUrl(text)
                if (url != null && UrlUtils.isSupportedUrl(url)) sharedUrl = url
            }
            Constants.ACTION_OPEN_APP -> {
                openQueue = true
            }
            else -> Unit
        }
        intent.removeExtra(Intent.EXTRA_TEXT)
    }

    /**
     * First launch only: asks for the runtime permissions the app needs (notifications and,
     * on older Androids, storage). The user can always request them again from Settings.
     */
    private fun askInitialPermissionsIfNeeded() {
        if (ServiceLocator.settings.cached.permissionsRequested) return
        val missing = Permissions.missingRuntimePermissions(this)
        ServiceLocator.scope.launch { ServiceLocator.settings.update { it.copy(permissionsRequested = true) } }
        if (missing.isEmpty()) return
        runCatching { permissionsLauncher.launch(missing) }
    }
}
