# ── jaudiotagger ──────────────────────────────────────────────────────────────
# Relies on reflection for tag frame classes — keep everything
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
