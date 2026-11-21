package com.ozone.player.appicon // TODO: change to the app's real package

import androidx.annotation.ColorRes
import com.ozone.player.R // TODO: change to the app's real R class

/**
 * The 4 Ozone Player launcher icons.
 * aliasClass must match the android:name of the matching <activity-alias> in the manifest
 * (see manifest/manifest_aliases.xml). These are plain strings, they do NOT need a real class.
 */
enum class AppIconVariant(
    val id: String,
    val label: String,
    val aliasClass: String,
    @ColorRes val backgroundRes: Int,
    @ColorRes val waveColorRes: Int,
) {
    LIME("lime", "Lime", "com.ozone.player.alias.IconLime", R.color.ozone_icon_lime, R.color.ozone_icon_black),
    LIME_WHITE("lime_white", "Lime & White", "com.ozone.player.alias.IconLimeWhite", R.color.ozone_icon_lime, R.color.ozone_icon_white),
    MIDNIGHT("midnight", "Midnight", "com.ozone.player.alias.IconMidnight", R.color.ozone_icon_black, R.color.ozone_icon_white),
    SNOW("snow", "Snow", "com.ozone.player.alias.IconSnow", R.color.ozone_icon_white, R.color.ozone_icon_black);

    companion object {
        /** Icon 1 (lime background, black wave). Must match the alias that has android:enabled="true" in the manifest. */
        val DEFAULT = LIME
    }
}
