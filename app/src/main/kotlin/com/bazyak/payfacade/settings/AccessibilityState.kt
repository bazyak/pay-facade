package com.bazyak.payfacade.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bazyak.payfacade.common.OwnActivityLaunchTracker
import com.bazyak.payfacade.main.PayFacadeAccessibilityService

/**
 * Whether our accessibility service is currently switched on.
 *
 * Nothing notifies an app when this changes, and it can only be changed on a system screen, so the
 * value is re-read every time the app comes back to the foreground.
 */
@Composable
fun rememberAccessibilityEnabled(context: Context): State<Boolean> {
    val enabled = remember { mutableStateOf(context.accessibilityServiceEnabled()) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled.value = context.accessibilityServiceEnabled()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

/** Opens the system screen where the service can be switched on. */
fun Context.openAccessibilitySettings() {
    OwnActivityLaunchTracker.isLaunchingOwnActivity = true
    startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun Context.accessibilityServiceEnabled(): Boolean {
    val target = "$packageName/${PayFacadeAccessibilityService::class.java.name}"
    val enabled = Settings.Secure.getString(
        contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    return enabled.split(':').any { it.equals(target, ignoreCase = true) }
}
