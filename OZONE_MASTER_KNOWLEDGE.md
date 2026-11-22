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
7. **Draggable "Liquid Lens" Navigation Bar & Kyant Backdrop Glass (`feature/advanced-liquid-glass`):**
   - **Kyant Backdrop (API 31+):** Real background optical refraction engine (`io.github.kyant0:backdrop` 2.0.1) for bottom nav bar fallback and mini player (`drawBackdrop` + `lensSafeGlassShape` + `0.10f` dark scrim + meniscus rim highlight).
   - **Fletchmckee Liquid Lens (API 33+):** `io.github.fletchmckee.liquid:liquid:1.1.1` draggable liquid lens navigation bar ported from LastWave:
     - 3-tab glass capsule rail (Home, Search, Library) with clear rounded lens over selected tab.
     - Interactive touch/drag: lens grows (+12%), trailing spring physics (`dampingRatio = 0.92f, stiffness = 540f`), rubber-banding at ends, dynamic chromatic dispersion (rainbow edge fringes only while pressed, 0 at rest).
     - Haptic feedback on tab crossings (`segmentTick`) and on release (`confirm`).
     - Acyclic rendering: Content Box A (`liquefiable(pageState)`) -> Clear Navigation Plane B (`liquefiable(railState)`) -> Sibling Lens C (`liquid(railState)`), preventing ghosting.
     - Gated to Android 13+ (API 33+) with fallback to Kyant Backdrop (API 31+) and Haze (< API 31).
     - **Layout & Mini Player Spacing Fix:** Insets/outer padding removed from `LiquidNavigationBar`; rail given exact 64dp height; `PlayerDockGeometry` and `ScaffoldAppState` updated to 64dp so mini player sits with exact 8dp gap above the rail without overlap or label clipping.
     - **Backdrop Isolation & Scrim Fix:** `ScaffoldShell` isolates `layerBackdrop` on an outer Box and `liquefiable` on an inner Box without conflicting `.background` modifiers. Mini player scrim set to dark translucent (`0.22f` black in dark theme via `pureBlack || isSystemInDarkTheme()`), restoring clear dark glass aesthetic.
8. **Search UI & Top Header Liquid Glass Alignment (Master Prompt 3):**
   - **Explore Search Capsule:** 56dp height, `RoundedCornerShape(28.dp)`, uses `backdropLiquidGlass` (blur 12dp, saturation 1.1, brightness +0.05, soft lens `lensDivisor = 8f, lensMaxRadiusDivisor = 3f`, dark scrim `0.18f`, press scale 1.02). Leading search icon `@ 0.9`, placeholder `@ 0.62`, globe `@ 0.85`. No thick borders or M3 container fills.
   - **Top App Bar / Header:** Flat grey slab and hard bottom divider removed. Replaced with seamless vertical gradient scrim `Brush.verticalGradient(bg @ 0.85f -> bg @ 0.0f)` over status bar + title row height; `TopAppBar` container colors set transparent.
   - **Search Overlay & Ghost Duplicate Elimination:** In `SearchScreen`, `SearchEntryField` is hidden with `alpha = 0f` and `clearAndSetSemantics {}` while `searchState.active` is true. `TopSearch` expanded overlay uses `background(background.copy(alpha = 0.96f))` over blurred backdrop; input row rendered as glass capsule with 16dp horizontal margin; content container uses `imePadding()` with 12dp spacing below the input row.
   - **Rainbow Edge Elimination:** Removed `liquidGlass` wrapping full-screen search content and removed rest dispersion (`dispersion = 0f` in `navigationRail`); rainbow dispersion strictly isolated to active nav lens drags.
   - **Calm Translucent History Rows:** 56dp height, `RoundedCornerShape(20.dp)`, 16dp horizontal margin, 8dp vertical gap, fill `White @ 0.06` (dark) / `Black @ 0.05` (light), solid 1dp hairline border `White @ 0.10`, 36dp circular leading icon container, 40dp trailing touch targets, and `Modifier.animateItem()`.

## 5. User Interaction & Language
- The user speaks conversational Manglish (Malayalam written in English letters).
- Sasi (CodeCraft AI) is the persona: warm, knowledgeable, proactive, and always replies in friendly Manglish.
- **For You (Personalized Trending & Smart Taste Auto-Detection):**
  - Section title & HeroMediaCard tag modernized to "For You" (with `auto_awesome` sparkles).
  - Hybrid architecture with `TrendingCategory` chips (`For You`, `Malayalam`, `Tamil`, `Hindi`, `English`, `Top Charts`) and source switcher (`YouTube Music`, `JioSaavn`, `Apple Music`).
  - Automatic taste detection (`detectUserTasteLanguage`) scans Unicode scripts (Malayalam, Tamil, Devanagari) and library artists to auto-serve regional hits matching the user's music taste.
  - `personalizeTracks` boosts user's top playtime artists (+0.80) to bring favorite artists to the front.
9. **Security, Stability & Playback Add-ups (`security-add-up`):**
  - **PlayerConnection Memory Leak Fix:** Disposed `PlayerConnection` in `onDestroy()`, passed `applicationContext` instead of activity context to prevent leaks across orientation and lifecycle events.
  - **20s Crossfade & Automix Duration:** Raised ceiling to 20s with 0.5s fine-grained slider steps and 16s/20s automix transition presets.
  - **Playlist & Library Direct Like Toggles:** Interactive heart button directly in local/online playlist song rows and Spotify library tracks.
  - **Robust Metadata Resolution (Spotify ID & ISRC):** Room query matching across YouTube ID, clean Spotify track IDs, and ISRC codes to seamlessly link songs with library state.
  - **Login Sheet IME & Scroll Gestures:** Prevented Compose bottom sheet from swallowing WebView touches during authentication; consumed scaffold insets to eliminate bottom whitespace above keyboard.
10. **JioSaavn Playback Resilience & Streaming Crash Remediation:**
  - **Decryption & Direct Stream Ingestion:** In `HomeTrendingSection.kt` and `OnlineSearchResult.kt`, JioSaavn tracks decrypt `encryptedMediaUrl` via `JioSaavnDecryptor`, store in `JioSaavnQualityCache`, and upsert both `MediaMetadata` and `FormatEntity` (with direct 320 kbps CDN URL) into Room DB before playback via `JioSaavnQueue`.
  - **ExoPlayer Loader Resilience:** In `MusicServicePlayback.kt:resolvePlaybackDataSpec`, lookup checks DB formats, then in-memory `JioSaavnQualityCache.getRawUrl(mediaId)`, followed by direct JioSaavn API lookup by song ID (`jioSaavnStreamResolver.resolveById(mediaId)`). If all JioSaavn CDN links fail or are expired, seamlessly resolves the song by title & artist on YouTube Music (with multi-stage `FILTER_SONG` -> `FILTER_VIDEO` -> `null` search) instead of throwing fatal `PlaybackException("JioSaavn audio stream unavailable")`.
  - **Player Error Auto-Recovery:** In `PlayerConnection.kt:onPlayerErrorChanged`, early source errors trigger tier fallback (320 -> 160 -> Original). If all tiers fail, smoothly searches and switches to the matching YouTube Music stream or skips to the next track without popping up error dialogs to the user.
  - **Chart Feed Parsing Fix:** In `TrendingRepository.kt:fetchJioSaavnTrendingFromNetwork`, JioSaavn playlist details parse songs from the root `list` array (falling back to `songs`), ensuring all trending songs contain decrypted CDN links.
11. **Playlist Download Sheet Duplicate Song Key Collision Remediation:**
   - **Crash Remediation:** When long-pressing the download button in local/online playlists, `PlaylistDownloadSheet` rendered `LazyColumn` songs using `key = { it.id }`. If a playlist contained duplicate tracks (e.g. video ID `GTDJcvAcQAw` repeated), Compose threw `IllegalArgumentException: Key was already used`.
   - **Fix:** Switched `PlaylistDownloadSheet` to `itemsIndexed` with unique compound key `key = { index, song -> "${song.id}_$index" }`, deduplicated songs internally via `songs.distinctBy { it.id }` for accurate download size estimation and UI counts, and ensured all callers (`LocalPlaylistScreen`, `OnlinePlaylistScreen`, `AlbumScreen`) pass deduplicated download items to `DownloadUtil`.
12. **Low-Quality Audio Enhancement (Enhance):**
   - **Real DSP Audio Chain Integration:** `EnhanceAudioProcessor.kt` in `app/src/main/kotlin/moe/rukamori/archivetune/playback/processor/` plugged directly into ExoPlayer's `DefaultAudioSink` audio processor array in `PlayerEngineHolder.kt` and `DualPlayEngine.kt`.
   - **DSP Pipeline:**
     - High-shelf cut (10.5–13 kHz, -3 dB to -7 dB) via Transposed Direct Form II Biquads to attenuate hiss and codec fizz.
     - Low-shelf boost (80–100 Hz, +1 dB to +3 dB) for acoustic body.
     - Zero-latency dynamic feed-forward compressor with smooth envelope follower (10 ms attack, 100 ms release) and makeup gain (+1.2 dB to +3.2 dB) to enrich quiet, thin recordings.
     - Hyperbolic tangent soft knee limiter for peaks > 0.9 to prevent digital clipping.
   - **Latency & Bit-Identical Bypass:** 0 sample latency. When toggled off or inactive, bypasses cleanly via raw direct `ByteBuffer` transfer, ensuring 100% bit-identical pass-through. Live toggle responds immediately without pausing or restarting playback.
   - **Settings & UI:** In Settings > Player and audio (`PlayerAudioSection.kt`):
     - "Enhance" switch with subtitle: "Cleans up hiss and thin sound on low-quality songs. Cannot restore detail that is missing from the file."
     - "Auto" mode option: automatically enables enhancement when current stream bitrate is below 160 kbps or Low Data mode is active, bypassing otherwise.
     - "Strength" slider with Light / Medium / Strong discrete presets.
     - Telemetry integration in `PipelineTelemetry.kt` and `SignalPathDialog.kt`.
13. **Estimated Word-by-Word Synced Lyrics:**
    - **Architecture:** Introduces estimated word-by-word lyric animation for providers returning line-timed (LRC) lyrics (LrcLib, SimpMusic, KuGou), leaving real word-timed providers (BetterLyrics, YouLyPlus) untouched.
    - **Mathematical Syllable Estimator (`LrcParser.kt`):** Parses fractional timestamps, clamps singing duration to `min(nextStart - 80ms, start + maxSing)` (800..8000ms), and weights words by `(length + 2)` to distribute duration naturally.
    - **Rendering & Badging:** Syllables feed into Accompanist `KaraokeLyricsView` via `buildSyncedLyrics`. Auto-prioritizes `WORD > LINE > PLAIN` and displays 'Estimated' badge on LINE results in `LyricsMenuProviders.kt`.
14. **TTML Lyrics & Background Vocals Support:**
    - **Timing Modes (`itunes:timing`):** Reads root `itunes:timing` attribute. `Word` mode uses `<span begin end>` word timing directly. `Line` mode estimates word timing per `<p begin end>` line using the line's provided begin and end times directly (without 120ms/char cap or next-line-start logic, using `length + 2` word weighting).
    - **Unified Time Parsing:** Supports `mm:ss.mmm`, `hh:mm:ss.mmm`, and `12.5s` formats across TTML and LRC parsers.
    - **Shared Word Splitter:** Unified `splitWords(start, end, text, isBackground)` function shared between TTML and LRC estimation paths.
    - **Metadata `lyricOffset`:** Protected behind `RESPECT_TTML_LYRIC_OFFSET` constant (OFF by default) to test whether provider timestamps already include audio offsets.
    - **Parenthetical Background Vocals:** Lines fully enclosed in parentheses `(...)` or `（...）` automatically render dimmer and smaller via `KaraokeLine.AccompanimentKaraokeLine` and styling tokens.
15. **Multi-Provider Lyrics Ecosystem (Ported from LastWave):**
    - **6 New Lyrics Providers:**
      1. `AppleMusicLyricsProvider`: Paxsenix Lyrically + iTunes entity search with direct TTML and v2 envelope fallback.
      2. `MusixmatchLyricsProvider`: HMAC-SHA256 request signing with token recycling for real LRC subtitle streams.
      3. `GeniusLyricsProvider`: Web scraping parser with HTML unescaping and annotation stripping.
      4. `BiniLyricsProvider`: Syllable-sync word-timed provider with ISRC recording identification.
      5. `LrcRedLyricsProvider`: Fast ISRC-based Apple TTML and LRC resolver via Bini metadata.
      6. `MegalobizLyricsProvider`: Direct HTML parser extracting community-timed LRCs.
    - **UI & Reordering:** Full settings switches in Settings > Lyrics (`LyricsProvidersSections.kt`), draggable priority order dialog, and live candidate detection per provider in player 3-dot lyrics menu (`LyricsMenuProviders.kt` & `LyricsCandidatesManager.kt`).
    - **Security & Classification:** All 6 boolean keys strictly classified in `PlainPreferences.kt` to guarantee passing security test checks.
