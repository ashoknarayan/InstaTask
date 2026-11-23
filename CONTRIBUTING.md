# Contributing to InstaTask

InstaTask is an offline-first Android app built with Kotlin, Jetpack Compose, Room, WorkManager, and on-device AI.

## Quick Start

1. Open the repository in Android Studio with JDK 17.
2. Sync Gradle.
3. Run `:app` on an API 26+ device or emulator.
4. V1 uses `FakeIntelligenceEngine` by default, so no 900 MB model download is required.

## Modules

- `:app` - Activity, application setup, and navigation.
- `:core:model` - Enums and pure domain models.
- `:core:database` - Room entities, DAOs, and converters.
- `:core:ai` - Intelligence interface, mock engine, prompts, and RAG.
- `:core:media` - Stopwatch foreground service and mood audio.
- `:core:worker` - Batch generation and permissioned web lookup.
- `:feature:feed` - Reels pager and interactions.
- `:feature:chat` - Onboarding and brain dumps.
- `:feature:vault` - Starred and liked reels.

## Pull Requests

- Keep modules isolated.
- Run `./gradlew testDebugUnitTest`.
- Run `./gradlew :app:assembleDebug` for a device-build check.
- Include a screenshot or screen recording for UI changes.
