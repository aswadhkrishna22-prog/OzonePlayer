### ✨ What's New in This Release

- 🛡️ **Crossfade BufferOverflowException Crash Fix**:
  - Completely resolved `Unexpected runtime error Code: 1000 (BufferOverflowException)` during Crossfade and Automix playback.
  - Isolated `SpatialProcessor` instances per audio sink, preventing thread-contention buffer overwrite between primary and transition decks.
  - Aligned exact frame buffer allocations with native byte order.

- 🎚️ **High-Power Output Gain (+30.0 dB) & Audio Effects Engine**:
  - Decoupled `LoudnessEnhancer`, `BassBoost`, and `Virtualizer` so they operate independently without requiring equalizer activation.
  - Expanded Output Gain pre-amp slider up to **+30.0 dB** (3000 mB) for massive speaker volume boost.
  - Fixed audio effect persistence across deck handoffs, crossfades, and track changes.
  - Raised minimum audio normalization headroom to 50% (`MIN_AUDIO_NORMALIZATION_FACTOR = 0.5f`), preventing YouTube Music tracks from being excessively attenuated.

- 🌐 **Together Online Live Relay Infrastructure**:
  - Integrated live high-speed Render relay server (`https://ozoneplayer.onrender.com`) for Together Online synchronized listening.
  - Instant room creation, 4-digit code matching, and auto-reconnecting WebSocket mesh.

- 🔊 **Audio Playback Stutter & Bluetooth Dropout Fix**:
  - Expanded audio buffer from 2.5s to **20.0s (min)** and **60.0s (max)** (`PRIMARY_MIN_BUFFER_MS` & `PRIMARY_MAX_BUFFER_MS`).
  - Completely eliminates audio cutouts and Bluetooth stuttering when searching songs, browsing playlists, scrolling home feed, or background multitasking.

- 💥 **Beast Mode High Power Audio & 3-Way Crossover Tuning**:
  - Boosted default Beast Mode gain to **+5.5 dB** (+550 mB) with Android `LoudnessEnhancer` safe lookahead limiter.
  - Tailored 3-Way Crossover EQ curve optimized for heavy party/tour bus speakers: deep 31–62Hz sub-bass (+3.5 dB), punchy 125–250Hz kick drums (+2.0 dB), clear vocal presence (+1.0 to +2.0 dB), and crisp tweeter sparkle (+2.5 to +3.5 dB).
  - Manual Equalizer customization up to **+30.0 dB** output gain without resetting Beast Mode.

- ⚡ **Smart Vibe & Energy Queue Engine**:
  - Intelligent mood & vibe detection (**Power/Dance**, **Love/Romantic**, **Emotional/Sad**).
  - Ensures continuous party energy: when starting playback from a high-energy dance track, upcoming automix queue songs are automatically prioritized to keep the dance vibe going rather than dropping into slow ballads.
  - Beast Mode automatically enforces Power/Dance priority across the entire radio queue.
  - User switchable under **Settings -> Player -> Smart Vibe Queue**.

- 💃 **Kemo Instant Music Dance Mode**:
  - As soon as any song plays (Player A, ExoPlayer, or Dual Play), Kemo instantly dances along with rhythmic bounce animations and floating musical note particles!
  - Fully reactive playback state synchronization ensuring smooth transitions between dancing, idle stroll, and sleep modes.

- 🐱 **Kemo Interactive Drag & Drop (Free Roam)**:
  - Touch and pick up Kemo to place her anywhere on the screen with tactile lift animations and particles.
  - Free Roam Placement: strolls, dances, and sleeps at custom heights.
  - Smart Dock Snap & Double-Tap Reset.

- ⚡ **Ultra-Fast Offline Downloads**:
  - Overhauled download stream resolution with unthrottled VisionOS fast path (~200ms resolution).
  - Direct local cache promotion for instant zero-data downloads.

---
