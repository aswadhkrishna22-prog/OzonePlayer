# OzonePlayer — Master Knowledge & Architecture Base (A to Z)

## 1. Project Background & Identity
- **Original Upstream:** YumaPlayer (`https://github.com/MuwMx/YumaPlayer.git`)
- **Fork / Active Repository:** `https://github.com/aswadhkrishna22-prog/OzonePlayer.git`
- **Application Name:** Ozone Player (rebranded from YumaPlayer).
- **Package / Namespace:** `moe.rukamori.archivetune`
- **Stack:** Modern Android, Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer), Room Database, Hilt/Dependency Injection, Coil for image/artwork loading, DataStore for preferences.

## 2. Critical Operational Rules
1. **Phone Overheating Constraint (STRICT):**
   - The user runs everything on their phone via Termux.
   - **NEVER** run local Gradle builds, Gradle tasks, local tests, or local lint (`./gradlew assembleDebug`, etc.).
   - All builds and checks must strictly run via **GitHub Actions** workflows (`.github/workflows/build_pr_debug_apk.yml` or `.github/workflows/build.yml`).
   - Poll GitHub Actions once per minute (`gh run watch` or `gh run list`) until green.
2. **Audio Engine Protection Rule (STRICT):**
   - Never touch the audio engine or playback service internals unless explicitly and directly instructed with specific audio processing tasks (e.g. `SpatialProcessor.kt`).
   - Playback service, audio sinks, and ExoPlayer codecs must remain pristine.
3. **Branching & PR Rules:**
   - Work on feature branches branched from `dev` (or `main` if `dev` doesn't exist).
   - Do NOT merge feature PRs into `dev` unless explicitly told by the user.

## 3. Player Layout & Gesture System
- **Player Layout Directory:** `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/`
  - `PlayerLayout.kt`: Main scaffold for the music player.
  - `FullPlayer.kt`: Full player screen controller.
  - `FullPlayerCoverSlot.kt`: Slot containing the album artwork.
  - `PlayerCoverCard.kt`: Album art card with horizontal drag gesture for next/previous track skips (`detectHorizontalDragGestures`).
  - `PlayerBackgroundLayers.kt`: Immersive full-bleed artwork rendering.
  - `DoubleTapSeekOverlay.kt`: Double-tap-to-seek gesture detector & water ripple animation overlay.
- **Dual Play System:**
  - Located in `dualplay/`: `DualPlaySplitView.kt`, `PaneSource.kt`.
  - Splits screen into two independent player panes (Player A and Player B).
  - Each pane has its own position flow (`paneSource.positionFlow`) and seek function (`paneSource.onSeek`).
- **Existing Gestures:**
  - Swipe Down: Sheet dismiss (`detectVerticalDragGestures`).
  - Horizontal Drag: Track skip forward/backward.
  - Double Tap on Artwork: Seeks back (left half) or forward (right half) with water ripple animation & step accumulation.

## 4. Key Features & Work Done
1. **Double Tap to Seek & Water Ripple Overlay (`feat/double-tap-seek`, PR #11):**
   - `DoubleTapSeekOverlay.kt`:
     - Double-tap left half: seek backward by step.
     - Double-tap right half: seek forward by step.
     - 700ms accumulation window (consecutive taps add up: "+10 s", "+20 s", "-30 s").
     - 3 water ripple rings animated on Canvas with `FastOutSlowInEasing` (radius 0 -> 0.55 width, travels 25% width, stretched 1.35x, quadratic alpha fade).
     - Reduced motion support (0 rings if animations disabled; 1 ring in battery saver).
     - Settings in Settings > Player: "Double tap to seek" (switch) & "Seek step" (5s, 10s, 15s, 30s).
2. **Batch Download & Song Selection:**
   - Long press selection menu with checkboxes/tick marks fixed.
3. **Spatial Audio Processor (`SpatialProcessor.kt`):**
   - Media3 BaseAudioProcessor with biquad shelving/peaking filters, crossfeed, and delay.
4. **CI / CD Pipelines:**
   - `.github/workflows/build_pr_debug_apk.yml`: Automatically builds normal debug APK and R8 release test APK on PRs and dispatches.

## 5. User Interaction & Language
- The user speaks conversational Manglish (Malayalam written in English letters).
- Sasi (CodeCraft AI) is the persona: warm, knowledgeable, proactive, and always replies in friendly Manglish.
