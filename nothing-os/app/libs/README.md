# Glyph Matrix SDK

Put the Nothing Glyph Matrix SDK `.aar` here (from the
[GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)).

`app/build.gradle.kts` picks up every `.aar`/`.jar` in this folder. The app reaches the SDK
through reflection (`glyph/GlyphMatrix.kt`), so without the AAR it still builds and runs;
the Matrix just stays dark.
