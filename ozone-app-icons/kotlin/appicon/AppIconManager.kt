package com.ozone.player.appicon // TODO: change to the app's real package

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {

    private fun component(context: Context, v: AppIconVariant) =
        ComponentName(context, v.aliasClass)

    /** Reads the REAL state from PackageManager (not a saved pref), so it is always correct. */
    fun current(context: Context): AppIconVariant {
        val pm = context.packageManager
        for (v in AppIconVariant.values()) {
            if (pm.getComponentEnabledSetting(component(context, v)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) return v
        }
        // Nothing explicitly enabled yet -> the manifest default alias is active.
        return AppIconVariant.DEFAULT
    }

    /**
     * Switches the launcher icon. Enables the new alias FIRST, then disables the others,
     * so the app never has zero launcher entries. The launcher may take a few seconds to refresh.
     */
    fun apply(context: Context, variant: AppIconVariant) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, variant),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        for (v in AppIconVariant.values()) {
            if (v == variant) continue
            pm.setComponentEnabledSetting(
                component(context, v),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
