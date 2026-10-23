package com.mradio.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.n2k02.updater.N2K02UpdateScreen
import dev.n2k02.updater.UpdateInstallGuard

private tailrec fun Context.findUpdaterActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findUpdaterActivity()
    else -> null
}

/**
 * Opt-in device-test UI only; no production screen calls this.
 * The host must supply its real safety guard (for example, stop active playback).
 */
@Composable
internal fun SharedUpdaterTestScreen(
    experimentalEnabled: Boolean,
    installedVersionCode: Long,
    installGuard: UpdateInstallGuard,
) {
    val context = LocalContext.current
    val activity = context.findUpdaterActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context.applicationContext, experimentalEnabled, installedVersionCode) {
        SharedUpdaterBridge.create(context.applicationContext, experimentalEnabled, installedVersionCode)
    }

    DisposableEffect(controller, lifecycleOwner, activity, installGuard) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && activity != null) {
                SharedUpdaterBridge.onResume(controller, activity, installGuard)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity != null) {
            SharedUpdaterBridge.onResume(controller, activity, installGuard)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SharedUpdaterBridge.cancel(controller)
        }
    }

    N2K02UpdateScreen(controller = controller, installGuard = installGuard)
}
