# Glyph Matrix SDK

CI downloads the Nothing Glyph Matrix SDK `.aar` from the official
[GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
into this folder on every build. Nothing's licence doesn't allow redistributing it, so it is
git-ignored. For a local build, copy the `.aar` here yourself.

`app/build.gradle.kts` picks up every `.aar`/`.jar` in this folder. The app reaches the SDK
through reflection (`glyph/GlyphMatrix.kt`), so without the AAR it still builds and runs;
the Matrix just stays dark.
