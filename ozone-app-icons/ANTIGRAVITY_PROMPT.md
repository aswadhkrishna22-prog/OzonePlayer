TASK: Install the new Ozone Player icon pack (zip contents are in the repo folder `ozone-app-icons/`, I will upload/commit it). Icon 1 (lime background, black wave) is the default. Add the other 3 as selectable icons. The intro animation must show whichever icon the user selected. README and the GitHub Pages site always use the default icon.

Work on a new branch: feat/app-icons. Do not touch anything unrelated to icons.

1) RESOURCES
- Copy `ozone-app-icons/app/src/main/res/*` into the app module's `src/main/res/`.
- BEFORE copying, delete the OLD launcher resources so there are no duplicate-resource build errors: every `ic_launcher*` and `ic_launcher_round*` file (.webp / .png / .xml) in all `mipmap-*` folders, and the old adaptive foreground/background/monochrome drawables ONLY if nothing else references them (grep first).
- Check minSdk. If minSdk >= 26, do NOT use `optional-legacy/`. If minSdk < 26, copy `optional-legacy/mipmap-*` too (and make sure no old .webp with the same name remains).
- Do not edit the vector paths, they are generated from the master artwork.

2) MANIFEST
- Follow `ozone-app-icons/manifest/manifest_aliases.xml` exactly: remove the MAIN/LAUNCHER intent-filter from the main activity, add the 4 `<activity-alias>` entries (only IconLime enabled). Use the real main activity name and keep the same android:label logic as the current activity/application, including for the debug "Ozone Dual Play" variant (applicationIdSuffix). Keep deep links and other intent-filters on the main activity.
- Keep `<application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round">`.

3) KOTLIN
- Copy `ozone-app-icons/kotlin/appicon/*.kt` into the app source set (e.g. `.../appicon/`). Fix the `package` line and the `R` import to the app's real namespace. Do not rewrite the logic.
- AppIconManager.current() reads the real state from PackageManager. AppIconManager.apply() enables the new alias first, then disables the others, with DONT_KILL_APP.

4) SETTINGS: Settings > Appearance > "Custom app icon"
- Add the item (if Appearance has no such section yet, put it where the existing appearance options are). Match the app's existing settings UI style exactly.
- It opens a picker (bottom sheet or screen, whichever the app already uses for similar pickers) with the 4 icons drawn using `OzoneLogo(variant, ...)`, label under each, and the current one marked selected.
- Initial selection = AppIconManager.current(context). Tapping one calls AppIconManager.apply(...) and updates the selection.
- Small note in the picker: "The launcher may take a few seconds to update the icon."

5) INTRO ANIMATION
- Find the existing intro/splash animation that shows the app icon/logo. Replace ONLY the icon drawing with `OzoneLogo(variant = rememberCurrentAppIcon(), ...)`. Keep the current timing, easing and layout of the animation as they are. If the animation looks better with it, drive `revealProgress` 0f -> 1f to wipe the wave in left to right, but do not change the rest of the intro.
- Read the variant once per launch (remember), so there is no flicker.
- If the Android 12+ system splash (windowSplashScreenAnimatedIcon) hardcodes the old icon, tell me, don't redesign it.
- If the About screen already shows the app icon, use OzoneLogo with the current variant there too.

6) README + WEBSITE (always the DEFAULT lime/black icon)
- README: copy `ozone-app-icons/readme/ozone-icon.svg` to `docs/assets/ozone-icon.svg` (and the 256px PNG next to it). Replace the old logo in the README header with the snippet from `readme/readme-header.md`.
- GitHub Pages site: copy `ozone-app-icons/web/*` into the site's root folder (the folder that is actually published), paste `web/head-snippet.html` into the `<head>`, and replace any old logo/hero/app-icon image on the page with `icon-512.png` (or `favicon.svg`). Fix paths if the site is served from a subfolder.

7) HARD RULES
- Do NOT run Gradle builds, tests, big scans or any other heavy terminal work on my phone (it overheats). Only edit files, commit and push. Builds and verification run ONLY through GitHub Actions.
- When done: list every file you added/changed/deleted, say which CI run/artifact I should install, and what I should check on the phone (default icon on home screen, switching icons in Settings, intro animation icon after switching).
