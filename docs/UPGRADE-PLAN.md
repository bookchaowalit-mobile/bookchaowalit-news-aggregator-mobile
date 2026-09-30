# Upgrade plan

## Current state

Score: 5/10 (was 4/10) — tested pure-Kotlin domain core and honest CI; the
Compose UI is still a placeholder and the Android build is unverified locally.

## Backlog

- P0: Confirm the `android` CI job is green (AGP 8.5 + Kotlin 2.0.21 Compose
  plugin + material dependency were fixed statically, without an Android SDK).
- P0: Replace the placeholder Home/Explore screens with UI backed by `core`
  (ViewModel + state holder calling the `core` API).
- P1: RSS/Atom parser in `core` (XmlPullParser-compatible or a small pull parser) with fixture-based tests.
- P1: Background refresh with WorkManager and an offline cache (Room).
- P2: Compose UI tests (`androidx.compose.ui:ui-test-junit4`) for the main flow.
- P2: Pin `distributionSha256Sum` in `gradle-wrapper.properties`.

## Done in this pass (pass 1)

- Added `core/` (included Gradle build) with the app's domain logic and 5 unit tests.
- Committed the Gradle wrapper (8.10.2).
- Fixed Kotlin 2.0 Compose setup: `org.jetbrains.kotlin.plugin.compose`
  replaces the removed `composeOptions.kotlinCompilerExtensionVersion`; Java/Kotlin
  target 17; added `com.google.android.material` for the manifest theme.
- CI: removed `|| true`; separate `core` and `android` jobs.
- README now states what is verified and what is not.

## Done in this pass (pass 2)

- Fixed de-duplication: a source repeating its own headline ("Morning briefing" every day) was dropped after the first day; title duplicates now require a *different* source within 48 h, as the doc comment promised.
- Fixed filtering: muting "ai" hid every article containing "said"; Latin terms now match at word starts, Thai/CJK terms (no spaces) still match as substrings.
- 7 more edge-case tests (12 total). Verified with `./gradlew -p core test --offline`.
- Still not verified locally: the Compose `app/` (no Android SDK; dl.google.com is blocked here).
