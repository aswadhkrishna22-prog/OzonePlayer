### ✨ What's New in This Release

- 📜 **TTML Timed Lyrics & Background Vocal Styling**:
  - Full support for TTML lyrics format returned by providers such as YouLyPlus.
  - Respects root `itunes:timing`: `Word` mode uses direct `<span begin end>` syllable timings, while `Line` mode estimates word timing per `<p begin end>` line using the line's provided begin and end times directly with word-weighting.
  - Multi-format time parser supporting `mm:ss.mmm`, `hh:mm:ss.mmm`, and `12.5s` timestamps.
  - Shared `splitWords(start, end, text)` algorithm across LRC and TTML estimation paths.
  - Lines fully inside parentheses render dimmer and smaller as background vocals via `AccompanimentKaraokeLine`.
  - Configurable `RESPECT_TTML_LYRIC_OFFSET` flag (default OFF) to verify whether begin times already incorporate audio offsets.

- 🎤 **Estimated Word-by-Word Synced Lyrics**:
  - Introduces smooth word-by-word karaoke animation for providers returning line-timed (LRC) lyrics (LrcLib, SimpMusic, KuGou), while leaving native word-timed providers (BetterLyrics, YouLyPlus) untouched.
  - Mathematical syllable-length estimator in `LrcParser` parses decimal fractions and distributes singing durations across words weighted by length.
  - Reuses the high-performance KaraokeLine rendering pipeline for silky-smooth lyric glow.
  - Automatically prioritizes `WORD > LINE > PLAIN` with manual choice and 'Estimated' badge in provider sheets.

- 🎛️ **Real DSP Audio Enhancer (Low-Quality Audio Fix)**:
  - Real DSP enhancement pipeline (`EnhanceAudioProcessor`) integrated directly into ExoPlayer audio sinks.
  - Transposed Direct Form II Biquads: high-shelf cut (10.5–13 kHz) to attenuate hiss and codec fizz, and low-shelf boost (80–100 Hz) for acoustic richness.
  - Zero-latency dynamic feed-forward compressor with envelope follower and tanh soft-knee peak limiter.
  - Customizable under **Settings -> Player and audio**: Switch, Auto mode (< 160 kbps or Low Data Mode), and Strength presets (Light, Medium, Strong).

- 💎 **Advanced Realistic Liquid Glass Engine**:
  - Optical Snell's Law lens refraction via AGSL `RuntimeShader` on API 33+, with domed depth curvature and boosted vibrancy.
  - Specular meniscus illumination (1dp directional rim highlight and top sheen) across Home top app bar, Search bar, Library chips, and Mini Player pill.
  - Interactive Draggable Liquid Lens navigation bar with trailing spring physics (`dampingRatio = 0.92f, stiffness = 540f`) and dynamic chromatic dispersion.

- 🎨 **Dynamic App Icon Pack**:
  - Added 4 brand-new selectable Ozone Player launcher icon variants switchable in Settings.

- 🛡️ **Playback Streaming Resilience & Crash Remediations**:
  - Direct 320 kbps CDN decryption and quality cache for JioSaavn tracks, with seamless automatic fallback to YouTube Music.
  - Resolved `IllegalArgumentException: Key was already used` in `PlaylistDownloadSheet` on playlists with duplicate songs.
  - Fixed `PlayerConnection` memory leaks across lifecycle events.

---
