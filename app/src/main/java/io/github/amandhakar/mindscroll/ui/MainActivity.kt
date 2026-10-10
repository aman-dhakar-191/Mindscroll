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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindscrollTheme {
                val context = LocalContext.current
                val settings = context.app.settings
                var permissions by remember { mutableStateOf(PermissionState.read(context)) }
                var onboarding by remember { mutableStateOf(!settings.onboardingDone) }

                // Permissions are granted in system screens; re-read them on the way back.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissions = PermissionState.read(context)
                }

                if (onboarding) {
                    // Opened again from the home screen: back returns there.
                    BackHandler(enabled = settings.onboardingDone) { onboarding = false }
                    OnboardingScreen(
                        permissions = permissions,
                        onPermissionsChanged = { permissions = PermissionState.read(context) },
                        onDone = {
                            settings.onboardingDone = true
                            onboarding = false
                        },
                    )
                } else {
                    HomeScreen(
                        permissions = permissions,
                        onOpenSetup = { onboarding = true },
                    )
                }
            }
        }
    }
}
