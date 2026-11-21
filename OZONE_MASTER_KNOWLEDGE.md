# OzonePlayer — Master Knowledge & Architecture Base (A to Z)

## 1. Project Background & Identity
- **Original Upstream:** OzonePlayer (`https://github.com/aswadhkrishna22-prog/OzonePlayer.git`)
- **Fork / Active Repository:** `https://github.com/aswadhkrishna22-prog/OzonePlayer.git`
- **Application Name:** Ozone Player (rebranded from OzonePlayer).
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
5. **Add Playlist (Import from Link):**
   - **Top Bar Button (`TopBarAddPlaylistButton.kt`):** Round tonal button (34dp inner circle, 48dp touch target, `R.drawable.link` icon, tooltip "Add Playlist") immediately next to profile button in Library screen.
   - **Bottom Sheet (`AddPlaylistBottomSheet.kt`):**
     - Title: "Add Playlist", Subtitle: "Paste a public playlist link".
     - Rounded URL text field with "Paste" button (reads from clipboard) and auto-fill if clipboard has a supported link.
     - Live service detection chip: Spotify, YouTube Music, YouTube, Unsupported.
     - Preview card: rounded cover art, title, track count, service badge.
     - Peach-accent primary button with loading spinner, disabled state, and error retry.
     - Duplicate detection & warning prompt if playlist is already in library.
   - **Provider Architecture (`moe.rukamori.archivetune.playlistimport`):**
     - `PlaylistProvider` interface with `SpotifyPlaylistProvider` and `YouTubePlaylistProvider`, easily extensible for other providers.
     - `SpotifyPlaylistProvider`: Public playlist URLs / URIs, guest TOTP token fallback (`SpotifyAuth.fetchGuestAccessToken()`) so users don't need to log in to import public Spotify playlists.
     - `YouTubePlaylistProvider`: Parses `music.youtube.com`, `youtube.com`, `youtu.be`, and `list=` IDs via `YouTube.playlist`.
     - `PlaylistImportRepository`: Persists `PlaylistEntity` with `browseId`/`spotifyId`, stores `MediaMetadata` and mappings in `PlaylistSongMap`, displays with source badge ("Spotify" / "YouTube synced"), and shows snackbar on success.
6. **Ultimate Liquid Glass Engineering (`feature/advanced-liquid-glass`):**
   - **Core Engine (`designsystem/.../ui/component/LiquidGlass.kt`):**
     - Unified optical liquid glass pipeline: Snell's Law lens refraction via AGSL `RuntimeShader` on API 33+, domed depth curvature, and boosted saturation/vibrancy (1.35x - 1.55x).
     - Specular meniscus illumination (`liquidBubbleHighlight`): 1.0dp directional rim highlight (75-85% alpha white from top-left, 28-35% alpha white from bottom-right), shaded meniscus bevel, and surface sheen.
     - Zero dark scrim/slab: uses translucent light glass tint (white ~0.08f) so underlying artwork and content glow through.
     - Multi-platform fallbacks: API 31-32 uses Compose `Haze` blur + tint + rim highlight; < API 31 uses clean translucent solid + rim highlight. Chromatic aberration disabled for phone thermal/GPU protection.
   - **Four Major UI Target Areas:**
     1. **Home Screen:** Floating `ScaffoldTopAppBar` uses `headerLiquidGlass` with deep frost (22dp) and top sheen, refracting live feed content scrolling underneath without competing with album artwork.
     2. **Search Screen:** `TopSearch` and `SearchEntryField` use `liquidGlass` with high-contrast text legibility, 1dp meniscus rim highlight, and smooth animation to full-screen search.
     3. **Library Screen:** `LibraryCollapsingHeader` uses `headerLiquidGlass`, while `LibraryFilterChipBar` chips use `liquidBubbleHighlight` with active/inactive states; playlist/album covers remain untouched.
     4. **Song Pill / Mini Player & Dock:** `PlayerSheetBackdrop.kt` and `FloatingNavigationToolbar.kt` share the exact same `LiquidGlassPreset.Pill` (4dp low blur, 18dp lens height, 28dp distortion, 8dp drop shadow, 1.35x saturation), with smooth crossfade to full player on sheet expansion.
   - **Backdrop Capture Sibling Rule:** `ScaffoldShell.kt` wraps content in a solid canvas background before `hazeSource`, ensuring glass elements remain siblings of the captured source node, preventing cyclic render crashes and eliminating sharp text bleed.

## 5. User Interaction & Language
- The user speaks conversational Manglish (Malayalam written in English letters).
- Sasi (CodeCraft AI) is the persona: warm, knowledgeable, proactive, and always replies in friendly Manglish.
