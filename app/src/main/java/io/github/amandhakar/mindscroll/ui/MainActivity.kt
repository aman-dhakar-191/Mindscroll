package io.github.amandhakar.mindscroll.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.mascot.LauncherIcon

class MainActivity : ComponentActivity() {
    /**
     * The home-screen icon follows the user's character and colour, but only once the app is out
     * of sight: switching it while the app is open makes some phones close the app.
     */
    override fun onStop() {
        super.onStop()
        val settings = app.settings
        LauncherIcon.apply(this, settings.mascotCharacter, settings.mascotPalette)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindscrollTheme {
                val context = LocalContext.current
                val settings = context.app.settings
                var permissions by remember { mutableStateOf(PermissionState.read(context)) }
                var onboarding by remember { mutableStateOf(!settings.onboardingDone) }
                var showLog by remember { mutableStateOf(false) }
                var showApps by remember { mutableStateOf(false) }
                var showMemories by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var tour by remember { mutableStateOf(false) }

                // Permissions are granted in system screens; re-read them on the way back.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissions = PermissionState.read(context)
                }

                // Apps and memories open from Settings, on top of it: Back returns there.
                if (showMemories) {
                    BackHandler { showMemories = false }
                    MoMemoryScreen(onBack = { showMemories = false })
                } else if (showApps) {
                    BackHandler { showApps = false }
                    TrackedAppsScreen(onBack = { showApps = false })
                } else if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        onBack = { showSettings = false },
                        onOpenApps = { showApps = true },
                        onOpenMemories = { showMemories = true },
                    )
                } else if (showLog) {
                    BackHandler { showLog = false }
                    DetectionLogScreen(onBack = { showLog = false })
                } else if (onboarding) {
                    // Opened again from the home screen: back returns there.
                    BackHandler(enabled = settings.onboardingDone) { onboarding = false }
                    OnboardingScreen(
                        permissions = permissions,
                        onPermissionsChanged = { permissions = PermissionState.read(context) },
                        onDone = { startTour ->
                            settings.onboardingDone = true
                            onboarding = false
                            tour = startTour
                        },
                    )
                } else {
                    HomeScreen(
                        permissions = permissions,
                        onOpenSetup = { onboarding = true },
                        onOpenLog = { showLog = true },
                        onOpenSettings = { showSettings = true },
                        tour = tour,
                        onTourDone = { tour = false },
                    )
                }
            }
        }
    }
}
