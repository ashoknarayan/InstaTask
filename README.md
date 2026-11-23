# InstaTask

InstaTask is an offline-first Android productivity feed. It turns user brain dumps and goals into lightweight, Markdown-based task and plan reels rather than generated video files.

> **V1 foundation:** The app currently uses deterministic fake AI and placeholder local embeddings. It is an open-source starter implementation, not a production AI planner. No model download, API key, or cloud AI service is required to build or run it.

## What is included

- Three Compose destinations: vertical feed, Talk With Me, and Starred/Liked Vault
- Room storage for plans, tasks, reels, comments, chat, and local memory chunks
- WorkManager batch processing with a mock intelligence engine
- Parent-plan advancement and context/time-aware feed ranking
- Focus stopwatch foreground service with notification controls and reflection handoff
- Explicit one-time permission flow before web search
- Apache-2.0 license

## Architecture

```text
app
├── feature:feed    VerticalPager, reel interactions, reflection sheet
├── feature:chat    Brain-dump input and Room-backed message history
├── feature:vault   Starred and liked archive
└── core
    ├── model       Shared domain types
    ├── database    Room entities, converters, DAO, database
    ├── ai          TaskIntelligenceEngine, fake engine, local RAG
    ├── media       Stopwatch service and mood audio controller
    └── worker      Batch planner, feed ranker, approved web lookup
```

Room is the local source of truth. Compose screens observe DAO `Flow`s; ViewModels write user actions to Room and enqueue WorkManager for background processing. Features depend on core modules; core modules do not depend on UI features.

For the full module contracts, data flows, function details, and implementation limitations, read [InstaTask-Low-Level-Design.md](./InstaTask-Low-Level-Design.md).

## Requirements

- Android Studio with JDK 17
- Android SDK 36
- Android device/emulator running API 26 or newer

## Build and run

1. Clone the repository and open it in Android Studio.
2. Allow Gradle sync to complete.
3. Run the `app` configuration on an API 26+ device or emulator.

Command line:

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

The default Hilt binding in `core/ai/.../AiModule.kt` selects `FakeIntelligenceEngine` and `DeterministicTextEmbedder`. The app runs without downloading LiteRT-LM weights. The fake engine uses simple keyword heuristics for predictable local development; it is not a substitute for a real language model.

## Pick up a starter issue

Look for GitHub issues labeled `good first issue` or `help wanted`. If you are new to the project:

1. Read the matching module section in the low-level design.
2. Check the module's existing patterns before adding a dependency or abstraction.
3. Prefer a focused change with a unit test for behavior.
4. Run `./gradlew testDebugUnitTest` and `./gradlew :app:assembleDebug`.
5. In your pull request, describe the behavior changed and include a screenshot for UI work.

Good starter areas include Compose accessibility and visual polish, feed/vault empty states, DAO tests, Markdown rendering tests, and improving fake-engine test fixtures. Avoid working on real model integration unless the issue explicitly covers model licensing, download integrity, device memory, and offline behavior.

## License

InstaTask is licensed under the [Apache License 2.0](./LICENSE).
