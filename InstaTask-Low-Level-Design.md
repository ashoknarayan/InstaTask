# InstaTask V1 - Complete Low-Level Design

**Document scope:** The complete current Android codebase in this repository  
**Build state:** Verified with `:app:assembleDebug`  
**Default AI:** Deterministic `FakeIntelligenceEngine`  
**Package root:** `com.instatask`  
**Min/target SDK:** API 26/API 36

---

## 1. Purpose

This document explains the implementation of InstaTask V1 at component, function, data, and decision level. It is based on the actual source code rather than only the original product specification.

It covers:

- Module boundaries and Gradle configuration
- Every application entry point and presentation component
- Every domain model and enum
- Room entities, converters, DAOs, and database provisioning
- AI abstraction and local RAG behavior
- Stopwatch and audio components
- WorkManager batch and web workers
- Navigation and interaction flows
- Error handling, current limitations, and implementation tradeoffs

---

## 2. System Summary

InstaTask is an offline-first Android application that represents tasks and goals as lightweight reel records containing Markdown text, reel type, mood, theme, and optional plan/task links.

The current architecture is:

```text
Compose Presentation
  :feature:feed
  :feature:chat
  :feature:vault
          |
          v
ViewModels and Android Services
  FeedViewModel
  TalkViewModel
  VaultViewModel
  StopwatchForegroundService
          |
          v
Domain and Background Services
  :core:media
  :core:worker
  :core:ai
          |
          v
Room and Local State
  :core:database
  :core:model
```

Main flows:

1. The app starts with a vertical feed.
2. A batch worker seeds four starter reels when the database is empty.
3. The user can like, star, comment, start a stopwatch, or approve web research.
4. Chat messages and comments are queued with `isProcessedByWorker = false`.
5. `InterSessionBatchWorker` extracts changes through `TaskIntelligenceEngine`.
6. The mock engine creates tasks, goals, motivations, or a web lookup request.
7. Room becomes the source of truth and Room `Flow` queries refresh UI screens reactively.

---

## 3. Repository Structure

```text
InstaTask/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/instatask/
│       │   ├── InstaTaskApp.kt
│       │   ├── MainActivity.kt
│       │   └── navigation/InstaTaskNavHost.kt
│       └── res/values/themes.xml
├── core/
│   ├── model/
│   │   └── src/main/java/com/instatask/core/model/
│   │       ├── Enums.kt
│   │       └── DomainModels.kt
│   ├── database/
│   │   └── src/main/java/com/instatask/core/database/
│   │       ├── Converters.kt
│   │       ├── Database.kt
│   │       ├── DatabaseModule.kt
│   │       ├── Entities.kt
│   │       ├── InstaTaskDao.kt
│   │       └── PlanMarkdownFormatter.kt
│   ├── ai/
│   │   └── src/main/java/com/instatask/core/ai/
│   │       ├── TaskIntelligenceEngine.kt
│   │       ├── FakeIntelligenceEngine.kt
│   │       ├── LocalRagRetriever.kt
│   │       └── di/AiModule.kt
│   ├── media/
│   │   └── src/main/java/com/instatask/core/media/
│   │       ├── MoodAudioController.kt
│   │       ├── StopwatchForegroundService.kt
│   │       └── StopwatchState.kt
│   └── worker/
│       └── src/main/java/com/instatask/core/worker/
│           ├── ConstrainedWebSearchWorker.kt
│           ├── FeedOrdering.kt
│           ├── FeedRanker.kt
│           └── InterSessionBatchWorker.kt
├── feature/
│   ├── feed/
│   ├── chat/
│   └── vault/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties
├── README.md
├── CONTRIBUTING.md
└── InstaTask-Low-Level-Design.md
```

Generated Hilt and KSP code under `build/` is not source-owned. It is recreated by the build and should not be committed or edited directly.

---

## 4. Gradle and Build Architecture

### 4.1 Settings

`settings.gradle.kts`:

- Names the project `InstaTask`.
- Configures Google Maven, Maven Central, and the Gradle plugin portal.
- Uses `FAIL_ON_PROJECT_REPOS`, preventing module-level repositories from bypassing central dependency resolution.
- Includes every core and feature module.

**Decision:** A multi-module project makes UI development independent from database, AI, media, and worker internals. Feature modules do not need to know implementation details of another feature.

### 4.2 Root Build

`build.gradle.kts` declares shared plugins with `apply false`:

- Android Application
- Android Library
- Kotlin Compose
- Kotlin Serialization
- KSP
- Hilt

Using `apply false` keeps plugins in the version catalog while allowing modules to opt in selectively.

### 4.3 Version Catalog

`gradle/libs.versions.toml` centralizes versions for all modules.

Important choices:

- AGP `9.1.0`
- Kotlin `2.2.10`
- Compose BOM `2025.12.00`
- Room `2.7.2`
- WorkManager `2.10.3`
- Media3 `1.11.0`
- Hilt `2.59.2`
- OkHttp `4.12.0`
- Jsoup `1.18.3`
- Kotlin Serialization `1.8.1`

**Reason:** Centralized versions prevent dependencies in separate modules from silently drifting. The Compose BOM controls compatible Compose library versions.

### 4.4 Module Dependency Rules

```text
:app
  -> all core and feature modules

:feature:feed
  -> model, database, ai, media, worker

:feature:chat
  -> database, worker

:feature:vault
  -> model, database

:core:worker
  -> model, database, ai

:core:ai
  -> model, database

:core:database
  -> model

:core:media
  -> model, database
```

This creates an acceptable V1 dependency direction: features depend on reusable cores, and no core module depends on a feature module.

### 4.5 Java and SDK Configuration

All modules use Java 17 source and target compatibility. This is the standard Android/Kotlin build choice for modern Gradle and avoids older Java APIs.

The app has:

- `compileSdk = 36`
- `minSdk = 26`
- `targetSdk = 36`
- `versionCode = 1`
- `versionName = "0.1.0-alpha"`

API 26 is the minimum because foreground service, notification channel, Media3, and modern Android APIs require a relatively current baseline.

### 4.6 Gradle Properties

`gradle.properties` sets:

- `android.disallowKotlinSourceSets=false`
- `org.gradle.jvmargs=-Xmx4g`

`android.disallowKotlinSourceSets=false` is needed to permit the AGP/KSP generated Kotlin source-set integration used by this toolchain. The 4 GB Gradle heap reduces memory pressure during KSP, Room, Hilt, Compose, and multi-module compilation.

---

## 5. Application Entry Point

### 5.1 Android Manifest

`app/src/main/AndroidManifest.xml` declares:

- Internet permission for explicit web lookup
- Foreground service permission
- Special-use foreground service permission
- Notification permission

**Internet is declared but not used automatically.** It is used only after a permission reel is tapped and `ConstrainedWebSearchWorker` is enqueued.

The manifest also:

- Registers `InstaTaskApp` as the application class
- Launches `MainActivity`
- Registers `StopwatchForegroundService` with `foregroundServiceType="specialUse"`
- Supplies a special-use subtype description

The service is `exported=false`, preventing unrelated applications from directly starting it.

### 5.2 Theme

`app/src/main/res/values/themes.xml` wraps the Android Material no-action-bar theme.

**Decision:** Jetpack Compose owns UI styling, so only a minimal system theme is needed.

### 5.3 `InstaTaskApp`

Location:

- `app/src/main/java/com/instatask/InstaTaskApp.kt`

```kotlin
@HiltAndroidApp
class InstaTaskApp : Application(), Configuration.Provider
```

#### `workerFactory`

The application injects Hilt's `HiltWorkerFactory`.

**Why:** Hilt worker factories allow workers to receive Room DAOs and AI/RAG dependencies through constructor injection.

#### `workManagerConfiguration`

The property builds WorkManager configuration and supplies `HiltWorkerFactory`.

**Why:** This is required for Hilt-assisted `@HiltWorker` classes.

#### `onCreate()`

On process startup, the app calls `FeedOrdering.enqueueBatch(WorkManager.getInstance(this))`.

Consequences:

- The application always requests the batch worker on process creation.
- The worker checks whether a starter deck is needed.
- `ExistingWorkPolicy.REPLACE` prevents older and newer feed refresh requests from accumulating unnecessarily.

### 5.4 `MainActivity`

Location:

- `app/src/main/java/com/instatask/MainActivity.kt`

#### `onCreate()`

It:

1. Creates the Android activity.
2. Calls `enableEdgeToEdge()` for system-bar-aware Compose UI.
3. Sets Compose content.
4. Applies a dark `MaterialTheme`.
5. Places the navigation scaffold in a full-screen `Surface`.

#### `InstaTaskNavHost()`

Navigation is implemented in `app/src/main/java/com/instatask/navigation/InstaTaskNavHost.kt`, separate from `MainActivity`. It creates a `NavController` and `NavHost` with `feed` as the default route.

Routes:

| Route | Screen | Navigation behavior |
|---|---|---|
| `feed` | `FeedScreen` | Default route |
| `chat` | `TalkWithMeScreen` | Entered from feed |
| `vault` | `VaultScreen` | Entered from feed |

`MainActivity` owns Android window setup and theme application only. Keeping route construction outside the Activity makes navigation easier to inspect and evolve without changing Activity lifecycle code.

---

## 6. Domain Model Layer

### 6.1 Module Purpose

`:core:model` is pure shared data:

- It does not query Room.
- It does not launch Android services.
- It does not depend on Compose.

This enables all database and feature modules to share stable types.

### 6.2 Enums

#### `ReelType`

```kotlin
TASK
PLAN
MOTIVATIONAL
PERMISSION
INFO
```

Purpose:

- `TASK`: starts stopwatch
- `PLAN`: in-place roadmap reel
- `MOTIVATIONAL`: encouragement reel
- `PERMISSION`: explicit network consent reel
- `INFO`: supporting insight reel

It is `@Serializable` because AI-generated structured values and future serialized payloads may need to exchange it.

#### `MoodCategory`

```kotlin
MOTIVATIONAL
PEACEFUL
ENERGETIC
SILENT
```

Purpose: maps reel mood to audio behavior:

- `MOTIVATIONAL` -> motivational track
- `PEACEFUL` -> peaceful track
- `ENERGETIC` -> energetic track
- `SILENT` -> no audio

`PERMISSION` reels use `SILENT` to break the normal scroll/audio rhythm.

#### `ActioniserMode`

```kotlin
STOPWATCH
PERMISSION_TICK
STAR
```

This enum determines the primary right-rail action:

- Task = stopwatch
- Permission = tick
- Plan/Motivational/Info = star

#### `TimeBucket`

```kotlin
MORNING
AFTERNOON
EVENING
NIGHT
ANY
```

Purpose: associates tasks with preferred execution periods. `ANY` means no strong period preference.

#### `StepStatus`

`COMPLETED`, `CURRENT`, `PENDING` are the three states used to format plan reels.

#### `PlanStatus`

`ACTIVE`, `COMPLETED`, `ARCHIVED` describes a parent plan lifecycle.

#### `TaskStatus`

`ACTIVE` and `COMPLETED` provide a small state machine for child work units.

#### `MemorySource`

Identifies why a memory chunk exists:

- `CHAT_DUMP`
- `ONBOARDING_WHY`
- `COMPLETED_WIN`
- `COMMENT_FEEDBACK`
- `WEB_SNIPPET`

The source is stored even though current cosine retrieval does not yet apply source filtering. It allows future ranking rules.

#### `StopwatchRunState`

`IDLE`, `RUNNING`, `PAUSED` describes the in-memory stopwatch state machine.

### 6.3 `PlanStep`

Fields:

- `index`: stable zero-based step position
- `title`: displayed instruction
- `status`: completed/current/pending state

It is serializable because Room stores the entire ordered step list as JSON.

### 6.4 `ReelUiModel`

This model combines reel metadata for presentation.

Fields:

- IDs
- Reel type
- Markdown
- Mood/audio/theme
- Permission query
- Like/star state
- Comment count

#### `actioniserMode`

A computed getter maps `ReelType` to `ActioniserMode`. The getter prevents UI code from duplicating the mapping.

### 6.5 `ActiveStopwatchState`

Fields:

- `taskId`: task currently timed
- `taskTitle`: notification and UI label
- `runState`: stopwatch state
- `accumulatedMs`: elapsed active time
- `lastResumedRealtimeMs`: Android monotonic timestamp from the last resume

#### `computeCurrentElapsedMs(nowRealtimeMs)`

Implementation behavior:

- `IDLE` -> `0`
- `PAUSED` -> accumulated milliseconds
- `RUNNING` -> accumulated milliseconds plus time since resume

The result is clamped to at least zero.

**Why `SystemClock.elapsedRealtime()`:** Wall-clock changes do not affect elapsed measurement. The specification also suggests epoch persistence, but this V1 implementation only holds the active state in memory plus a Room update on stop.

---

## 7. Database Layer

### 7.1 Room Entities

#### `PlanEntity`

Table: `plans`

Fields:

- `planId`: primary key
- `goalTitle`
- `planReelId`: living Plan Reel ID
- `steps`: ordered `PlanStep` list
- `currentStepIndex`
- `status`
- `updatedAtEpochMs`

**Reason:** The plan has a direct reference to its reel. Completion mutates the existing `PlanEntity` and reel rather than creating duplicate plan rows.

#### `TaskEntity`

Table: `tasks`

Indices:

- `parentPlanId`
- `status`

Fields:

- `taskId`: primary key
- `parentPlanId`: optional parent plan
- `stepIndex`: matching plan step
- `title`
- `preferredTimeOfDay`
- `isUrgentTimeCrunch`
- `isRecurringDaily`
- `totalFocusedMs`
- `status`
- `createdAtEpochMs`

**Reason:** Standalone tasks have no parent or step. Plan-created tasks retain a parent and step index so plan completion can advance correctly.

#### `ReelEntity`

Table: `reels`

Indices:

- `inActiveSessionDeck`
- `isStarred`
- `linkedPlanId`

Fields:

- `reelId`: primary key
- Reel type and linked IDs
- Markdown content
- Mood and theme metadata
- Permission query and approval state
- Like/star state
- Star timestamp
- Active deck state
- Session rank
- Creation timestamp

**Reason:** Reels remain lightweight. Markdown and metadata avoid a heavier media pipeline.

#### `CommentEntity`

Table: `comments`

Indices:

- `reelId`
- `isProcessedByWorker`

Fields:

- Comment ID
- Reel ID
- Optional linked task
- Comment text
- Optional stopwatch duration
- Worker processing flag
- Creation timestamp

**Reason:** Comments are a durable queue consumed asynchronously. A comment may also carry the focus session duration.

#### `ChatMessageEntity`

Table: `chat_messages`

Fields:

- Message ID
- Whether the message is from the user
- Text
- Worker processing flag
- Creation timestamp

Only user messages are inserted by the current UI.

#### `MemoryChunkEntity`

Table: `memory_chunks`

Fields:

- Chunk ID
- Source type
- Text content
- 384-element `FloatArray`
- Creation timestamp

**Reason:** It combines text and vector storage in Room, avoiding a native vector database dependency.

### 7.2 Converters

#### `fromPlanSteps`

Serializes `List<PlanStep>` using Kotlin Serialization JSON.

#### `toPlanSteps`

Returns an empty list for blank text and otherwise decodes the stored JSON. Unknown fields are ignored.

**Reason:** Unknown future fields should not prevent loading older plans.

#### `fromFloatArray`

Converts a `FloatArray` to a little-endian `ByteArray`.

Every float occupies four bytes.

#### `toFloatArray`

Reads little-endian floats into a `FloatArray`.

**Reason:** Room cannot directly store `FloatArray` in the schema; a BLOB converter is compact and simple.

### 7.3 Database

`InstaTaskDatabase` declares version `1`, six entities, and `Converters`.

No schema migration is provided. `fallbackToDestructiveMigration()` recreates incompatible schema versions by dropping and rebuilding data.

**Current limitation:** It is appropriate for alpha development but unsafe for a production user database. A future version needs explicit migrations and backup/export behavior.

### 7.4 Database Provisioning

`DatabaseModule` is a singleton Hilt module.

#### `provideDatabase`

Creates:

```text
instatask.db
```

using `Room.databaseBuilder`.

#### `provideDao`

Exposes `InstaTaskDao` from the singleton database.

**Decision:** Singleton scope ensures one database connection and consistent DAO/Flow state across all workers and screens.

### 7.5 `InstaTaskDao`

Room DAOs are suspend functions for writes and one-shot reads; reactive feed/vault queries return `Flow`.

#### Feed and vault reads

- `observeActiveSessionDeck()`: active reels ordered by rank, maximum 60.
- `observeStarredReels()`: starred reels ordered by star time descending.
- `observeLikedReels()`: liked reels ordered by creation time descending.
- `getRecentLikedReels()`: latest five likes for future style conditioning.
- `getReel()`: one reel lookup.
- `observeCommentsForReel()`: chronological comments for a reel.

**Decision:** UI never manually polls Room. Reactive flow invalidation provides updates after worker or engagement mutations.

#### Engagement mutations

- `setReelLiked()`
- `setReelStarred()`
- `approvePermissionReel()`

Star mutations set `starredAtEpochMs` to `null` when unstarred. Like and star are deliberately separate signals, preserving the product philosophy that likes influence style while stars are bookmarks.

#### Comment and chat writes

- `insertComment()`
- `insertChatMessage()`

Both use `REPLACE`; identical IDs overwrite existing records. UUIDs make accidental ID collision negligible.

#### Worker delta reads

- `getUnprocessedChatMessages()`
- `getUnprocessedComments()`

They read only records with `isProcessedByWorker = false` in ascending creation order.

#### Worker delta completion

- `markChatsProcessed()`
- `markCommentsProcessed()`

They mark processed IDs after successful delta handling.

**Current correctness tradeoff:** Processing state is marked after the worker's operations in this implementation. If a worker is killed during `doWork()` after database writes but before marking IDs, duplicate processing is possible because it uses replace/upsert operations and keyword-based fake AI.

#### Plan/task operations

- `getActiveTasks()`
- `getPlanById()`
- `addFocusedTime()`
- `markTaskCompleted()`
- `deleteTaskReelsForTask()`
- `updatePlan()`
- `updatePlanReelMarkdown()`
- `upsertTask()`
- `upsertPlan()`
- `upsertReels()`

#### RAG operations

- `insertMemoryChunk()`
- `getAllMemoryChunks()` loads up to 2,000 chunks newest-first.

**Current limitation:** Similarity is calculated over the in-memory list. It is deterministic and simple but not scalable beyond the 2,000-row cap.

#### Starter check

`reelCount()` determines whether starter reels are needed.

### 7.6 `PlanMarkdownFormatter`

`renderPlanReel(goalTitle, steps)` produces:

- Plan heading
- Ordered plan list
- Completed marker `✅`
- Active marker `➡️`
- Pending marker `⬜`
- Strikethrough for completed steps

Steps are sorted by `index` regardless of input order.

**Decision:** Markdown formatting is deterministic and independent of the LLM, so plan progress remains structurally stable.

---

## 8. AI and RAG Layer

### 8.1 `TaskIntelligenceEngine`

This interface is the boundary between workers and all AI implementations.

`isModelReady()`:

- Reports whether the selected implementation is ready.

`extractUserDelta(rawNotesAndComments, activeTasks)`:

- Accepts combined chat/comment text.
- Accepts active task ID/title pairs.
- Returns structured output as `ExtractedDelta`.

`generateReelMarkdown(...)`:

- Generates one reel from a type and subject.
- Accepts RAG context and liked style examples.

**Why interface-first:** UI and workers can run with fake or future real AI implementations without rewriting call sites.

### 8.2 Structured Delta Types

#### `ExtractedTaskDraft`

Contains:

- Title
- Preferred time bucket
- Urgency flag
- Recurring flag

#### `ExtractedGoalDraft`

Contains:

- Goal title
- Ordered steps
- External facts requirement
- Suggested search query

#### `ExtractedDelta`

Contains:

- New tasks
- New goals
- Completed task IDs
- Core motivations
- Optional web lookup query

`@Serializable` keeps this contract usable by JSON-based model runners or parsers later.

### 8.3 `FakeIntelligenceEngine`

This is the default implementation bound at build time.

#### `isModelReady()`

Always returns `true`; no model download or initialization is required.

#### `extractUserDelta()`

Behavior:

1. Simulates 150 ms of inference with `delay`.
2. Combines notes into lowercase text.
3. Marks active tasks completed when text contains `"done"`, `"completed"`, or the task title.
4. Creates a task from the last note if no task is marked complete.
5. Infers time by keyword matching:
   - morning
   - afternoon
   - evening
   - night
6. Marks urgency for `"tomorrow"`, `"urgent"`, or `"deadline"`.
7. Stores the last goal-like note or `"because"` text as motivation.
8. Sets a web query for text containing `"learn"` or `"research"`.

**Limitations:** This is deterministic and intentionally heuristic. It should not be treated as production natural-language extraction.

#### `generateReelMarkdown()`

Returns templates for each reel type.

Template decisions:

- Task: tells the user to start stopwatch and reflect
- Plan: provides one active and one pending roadmap line
- Motivational: uses RAG's first memory as a quote
- Permission: gives an explicit approval/deny explanation
- Info: uses first RAG snippet or a fallback insight

`likedStyleExamples` is accepted but unused in V1. Its existence reserves the interface contract for real style conditioning.

### 8.4 RAG Embedder

#### `OnDeviceTextEmbedder`

One-method interface:

```kotlin
suspend fun embed(text: String): FloatArray
```

#### `DeterministicTextEmbedder`

Creates a 384-dimensional vector.

For each character:

1. Convert the character to a code.
2. Compute a bucket:

```text
(characterCode * 37 + characterIndex) % 384
```

3. Increment that bucket.
4. Normalize the vector using Euclidean norm.

**Why 384:** It reserves compatibility with the intended all-MiniLM-L6-v2 embedding shape.

**Limitations:** Character-hash embedding is not semantic. It is a deterministic placeholder until a real on-device embedder replaces it.

### 8.5 `LocalRagRetriever`

#### `indexMemory(source, text)`

1. Trims whitespace.
2. Limits text to 300 characters.
3. Ignores blank text.
4. Embeds the text.
5. Creates a UUID chunk ID.
6. Stores source, text, vector, and timestamp.

**Decision:** Truncation controls prompt and storage growth. Source names keep provenance even though source-aware ranking is not implemented.

#### `retrieveTopK(queryText, k)`

1. Loads up to 2,000 memory chunks.
2. If empty, returns no context.
3. Embeds the query.
4. Computes cosine similarity against every chunk.
5. Sorts descending.
6. Returns up to `k` text values.

#### `cosineSimilarity`

Calculates:

```text
dot(a, b) / (sqrt(dot(a,a)) * sqrt(dot(b,b)))
```

It returns zero for mismatched vector sizes or zero denominator.

**Decision:** Cosine similarity ignores vector magnitude and is suitable for a compact in-memory top-K search.

### 8.6 Hilt AI Bindings

`AiModule` binds:

- `FakeIntelligenceEngine` -> `TaskIntelligenceEngine`
- `DeterministicTextEmbedder` -> `OnDeviceTextEmbedder`

Both bindings are singletons.

**Future change:** Replace each binding with a real implementation without changing worker or feature call sites.

---

## 9. Media and Stopwatch Layer

### 9.1 `StopwatchState`

This singleton owns UI-visible stopwatch state as a `StateFlow`.

Initial state:

- No task
- Empty title
- `IDLE`
- Zero elapsed
- Zero resume timestamp

The read-only `StateFlow` prevents UI code from publishing invalid state directly.

#### `start(taskId, title, now)`

Sets:

- Provided task ID and title
- `RUNNING`
- Accumulated time zero
- Resume timestamp `now`

It represents one active stopwatch start only. Starting a second task overwrites the previous in-memory state.

#### `pause(now)`

Only pauses if `RUNNING`.

It computes elapsed time and sets:

- `accumulatedMs`
- `PAUSED`

#### `resume(now)`

Only resumes if `PAUSED`.

It sets the state to `RUNNING` and records a new monotonic resume timestamp.

#### `stop(now)`

1. Computes final elapsed time.
2. Creates the completed result.
3. Resets state to `IDLE`.
4. Returns the completed result.

**Design reason:** The result is available to the service even though the singleton reset follows immediately.

### 9.2 `StopwatchForegroundService`

The service is Android-entry-point-enabled for Hilt injection.

Injected dependencies:

- `StopwatchState`
- `InstaTaskDao`

It uses a supervisor coroutine scope with `Dispatchers.IO` to persist focused time.

#### Intent actions

| Action | Purpose |
|---|---|
| `ACTION_START` | Start foreground stopwatch |
| `ACTION_PAUSE` | Pause state |
| `ACTION_RESUME` | Resume state |
| `ACTION_STOP` | Stop and persist |

Extras:

- `EXTRA_TASK_ID`
- `EXTRA_TASK_TITLE`
- `EXTRA_ELAPSED_MS`
- `EXTRA_COMPLETED_TASK_ID`

The elapsed and completed-task extras are reserved; completion currently travels through `StopwatchState.completedSessions`.

#### `onBind()`

Returns `null` because this is a started service, not a bound Android service.

#### `onStartCommand()`

The service obtains `SystemClock.elapsedRealtime()` once per intent.

**Start:**

1. Reads task ID and title.
2. Calls `StopwatchState.start()`.
3. Calls `startForeground()` with notification ID `1001`.

**Pause:**

Calls `StopwatchState.pause()` and replaces the ongoing notification with a Resume action.

**Resume:**

Calls `StopwatchState.resume()` and replaces the ongoing notification with a Pause action.

**Stop:**

1. Calls `StopwatchState.stop()`.
2. If task ID exists, increments `totalFocusedMs` asynchronously.
3. Emits `StopwatchCompletedEvent(taskId, elapsedMs)` into the singleton state's pending `StateFlow`.
4. Removes foreground status.
5. Stops the service.

The method returns `START_STICKY`. This tells Android to recreate the service if it is killed, but no `onStartCommand` reconstruction data is stored. This is not a complete persistence strategy.

#### `onDestroy()`

Cancels the service coroutine scope.

#### `notification()` and `servicePendingIntent()`

Creates low-importance channel `instatask_focus`.

Notification content:

- Title: `Focusing: <task title>`
- Text indicates running or paused
- Dynamic Pause/Resume action
- Stop action
- Ongoing and non-dismissable by normal user swipe

**Decision:** The low-priority ongoing notification prevents accidental closure and complies with foreground stopwatch UX.

`servicePendingIntent()` creates immutable explicit PendingIntents with distinct request IDs so Android routes each notification action back to this service.

The FeedViewModel observes the pending completion state, finds the task's current reel through the DAO, and publishes it to FeedScreen. The resulting CommentSheet is pre-associated with that task and stores elapsed milliseconds when the reflection is submitted. The event remains pending until the sheet is submitted or dismissed, so temporary absence of a UI collector does not drop the completion.

**Current limitation:** The event channel is process-local; active stopwatch state is not persisted across process death.

### 9.3 `MoodAudioController`

The singleton owns one ExoPlayer instance.

#### `play(mood)`

- `SILENT` pauses.
- Motivational maps to resource `motiv_01`.
- Peaceful maps to `peace_01`.
- Energetic maps to `energ_01`.
- Missing resources return silently.

When an ExoPlayer is first created:

- Repeat mode is set to `REPEAT_MODE_ONE`.
- A MediaItem is set from the Android resource URI.
- The player prepares and plays.

**Decision:** A single ExoPlayer instance avoids overlapping tracks.

#### `pause()`

Pauses the active player.

#### `release()`

Releases native/exo resources and clears the reference.

#### `tryResource(name)`

Maps a raw-resource name to an integer resource ID.

**Current limitation:** No six-asset set exists in `res/raw` yet, and lifecycle-based play/pause is not wired to `ProcessLifecycleOwner` or a reel page change.

---

## 10. WorkManager and Batch Processing

### 10.1 `FeedOrdering`

`enqueueBatch(workManager)` builds a one-time constrained request for `InterSessionBatchWorker`.

The only constraint is battery-not-low.

Unique work name:

```text
instatask-inter-session
```

Existing work policy:

```text
APPEND_OR_REPLACE
```

**Reasons:**

- Battery-not-low avoids unnecessary background inference during low battery.
- Unique work prevents duplicate queue entries.
- Append avoids cancelling a running batch; a newer request is processed after the current unique-work item.

**Missing constraint:** The original specification discussed network constraints for local work, but batch AI does not require network.

### 10.2 `InterSessionBatchWorker`

This Hilt-assisted `CoroutineWorker` is the central batch orchestration component.

Constructor dependencies:

- DAO
- `TaskIntelligenceEngine`
- `LocalRagRetriever`

#### `doWork()`

Step-by-step behavior:

1. Load unprocessed chats.
2. Load unprocessed comments.
3. Extract the AI delta outside the database transaction.
4. Persist the generated changes, process flags, starter recovery, and deck ranks in one Room transaction.
5. Return success.

The transaction boundary is important: an exception during entity writes rolls back both generated data and processed flags. Input IDs are marked only after all corresponding output mutations, preventing a partially applied delta from being treated as complete. Input fetched by another queued worker after this invocation's initial read is not accidentally marked because only the fetched IDs are updated.

#### Completed task processing

For each AI-reported completed task:

1. Find task in the active task snapshot.
2. If missing, skip.
3. Mark task completed.
4. Delete matching task reels.
5. Index `Completed: <title>` as a completed-win memory.
6. If the task has a parent plan, advance that plan.

**Decision:** An active task snapshot is taken once, so all processing in the worker uses consistent input.

#### Standalone task processing

For each extracted task draft:

1. Create UUID task ID.
2. Create `TaskEntity`.
3. Store it.
4. Choose:
   - `MOTIVATIONAL` for urgent tasks
   - `TASK` otherwise
5. Retrieve top RAG context.
6. Generate Markdown.
7. Choose motivational or energetic mood.
8. Insert a reel linked only to the task.

#### Goal processing

For each extracted goal draft:

1. Create plan and plan-reel UUIDs.
2. Map each ordered step to `PlanStep`.
3. Mark step zero `CURRENT`.
4. Mark remaining steps `PENDING`.
5. Insert active `PlanEntity`.
6. Render plan Markdown deterministically.
7. Insert one Plan Reel.
8. Create an active first task.
9. Create the first Task Reel.

**Current limitation:** The fake AI currently does not emit `newGoals`, so this branch is exercised by alternate/test engines rather than ordinary mock-engine usage.

#### Web permission processing

If `needsWebLookupQuery` exists:

1. Create a black/silent Permission Reel.
2. Set `permissionQuery`.
3. Store the reel in the active deck.

No approval is granted until the user taps it.

#### `ensureStarterDeckIfNeeded()`

If the active session deck is empty, it restores four starter records:

1. Motivational card
2. Talk With Me card
3. One-task prompt
4. Simple weekly reset plan

Starter IDs are stable. If a starter reel already exists, it is reactivated rather than replaced, preserving saved state. Checking the active deck rather than total reel count permits recovery after all current-deck entries have been archived/deactivated.

#### `advanceParentPlanInPlace()`

1. Load plan by ID.
2. If missing, stop.
3. Find the next step by ordered index.
4. Mark steps at or before completed index `COMPLETED`.
5. Mark the next step `CURRENT`.
6. Mark later steps `PENDING`.
7. Set plan status completed if no next step exists.
8. Update plan entity.
9. Rewrite its existing Plan Reel Markdown.
10. If a next step exists and has no active task already, create the next active `TaskEntity`, retrieve relevant memories, generate task Markdown, and insert a linked TASK Reel.

`rankAndPersistActiveDeck()` ranks all active deck candidates, writes their rank values, and flips any candidates beyond the first 60 to inactive. Starred/liked records remain stored for vault access.

### 10.3 `FeedRanker`

`rank(reels, tasks, activePlans, nowBucket, jitter)` computes scores from the current data:

- Urgency: `+4.0`
- Preferred time match: `+1.5`; ANY: `+0.4`; non-match: `+0`
- Active plan current step task: `+2.0`
- Active plan reel: `+0.8`
- Reel variety baseline: small score by type
- Motivational mood for an urgent task: `+0.25`
- Bounded jitter: `0..0.35`

It sorts descending and returns copies with `sessionOrderRank` set to the computed score. The jitter function is injectable so tests can set it to zero and assert exact priority ordering.

`nowBucket(hour)` maps current hours:

| Hour | Bucket |
|---|---|
| 05-11 | MORNING |
| 12-16 | AFTERNOON |
| 17-20 | EVENING |
| Other | NIGHT |

The worker supplies active tasks/plans and current local time bucket, persists ranks, and limits the active deck to 60 entries. The DAO orders active feed rows by stored rank descending.

### 10.4 `ConstrainedWebSearchWorker`

This worker accepts:

- `query`
- `reelId`

#### `doWork()`

1. Read and truncate query to 180 characters.
2. Missing query returns failure.
3. Create OkHttp client with a 10-second call timeout.
4. URL-encode the query.
5. Request DuckDuckGo HTML search.
6. Supply a browser-like mobile User-Agent.
7. Retry when HTTP call fails.
8. Parse `.result__snippet` elements with Jsoup.
9. Join four snippets.
10. Limit text to 1,500 characters.
11. Retry if no snippets are found.
12. Index snippets as `WEB_SNIPPET` memory.
13. Mark/rewrite the reel as research approved.
14. Return success.

**No network occurs without user approval:** The reel action `approvePermission()` is the only code path enqueuing this worker.

**Current limitation:** DuckDuckGo HTML structure is external and can change. It has no domain allowlist, text-source verification, or cached response.

---

## 11. Feed Feature

### 11.1 `FeedViewModel`

The feed feature uses `AndroidViewModel` because it needs application context for intents and WorkManager.

Constructor injections:

- `Application`
- `InstaTaskDao`
- `StopwatchState`

#### `reels`

The DAO Flow is converted with:

- `SharingStarted.WhileSubscribed(5_000)`
- Initial empty list

**Reason:** Shared state remains active while the screen subscribes, stops after five seconds of no subscribers, and prevents room emission from requiring direct polling.

#### `toggleLike(reel)`

Launches a coroutine and inverts `reel.isLiked`.

It does not maintain a separate like-style list. The Room field is enough for future/limited style retrieval.

#### `toggleStar(reel)`

Launches a coroutine and:

- Inverts `isStarred`
- Sets `starredAtEpochMs` to now when starring
- Sets it to `null` when unstarring

#### `addComment(reel, text, elapsedMs)`

1. Reject blank text.
2. Launch a coroutine.
3. Create a UUID comment.
4. Store reel/task linkage.
5. Store optional stopwatch duration.
6. Store timestamp.
7. Enqueue the batch worker.

**Decision:** Comments are durable before background processing. UI code only queues work.

#### `startTask(reel)`

Creates an explicit foreground-service intent:

- Action `ACTION_START`
- Task ID
- Task title derived from first Markdown line

Then calls `startForegroundService`.

**Current gap:** It sends Markdown heading text instead of an actual task entity title.

#### `pauseStopwatch()`

Starts or sends a service intent with `ACTION_PAUSE`.

#### `resumeStopwatch()`

Starts or sends a service intent with `ACTION_RESUME`.

#### `stopStopwatch()`

Sends `ACTION_STOP` to the service. The service is the single authority for final elapsed-time calculation.

#### `approvePermission(reel)`

1. Reject missing query.
2. Create WorkManager input data.
3. Enqueue `ConstrainedWebSearchWorker`.

### 11.2 `FeedScreen`

The screen:

- Collects reels as Compose state.
- Shows a progress indicator for an empty deck.
- Uses `VerticalPager` for full-screen vertical paging.
- Keeps the selected comment reel as local UI state.

**Decision:** The empty state is a spinner even though starter-worker seeding usually follows immediately. It avoids a separate empty-feed layout.

#### `VerticalPager`

One page equals one reel. Page count comes from the current Room deck.

#### Bottom navigation

The bottom row contains:

- Vault text button
- Central FloatingActionButton
- Talk text button

Both central and Talk buttons navigate to chat.

#### Right rail

Every reel card has:

- Like button
- Comment button
- Context-specific Actioniser

The Actioniser dispatches by:

- `TASK` -> stopwatch
- `PERMISSION` -> web approval
- `PLAN/MOTIVATIONAL/INFO` -> star

Actioniser icons are explicit:

- Task -> timer
- Permission -> check
- Plan/Motivational/Info -> star

#### `ReelCard`

Behavior:

- Permission reel has a solid black gradient.
- Other reels use a dark navy-to-black gradient.
- Supported reel content is rendered by `multiplatform-markdown-renderer-m3`.
- `isMarkdownSafeForRenderer()` routes oversized input, NUL-containing content, and unclosed code-fence blocks to plain text fallback.
- `PlainReelText()` caps fallback display at 10,000 characters.

**Decision:** A Compose-native Markdown renderer supports headings, bold, lists, and plan markers without a WebView. The fallback handles malformed or excessively large payloads conservatively.

#### `RailButton`

Creates a circular `FilledIconButton` plus small label.

#### `CommentSheet`

Uses `ModalBottomSheet`.

Local text state holds the user's reflection. On submit:

1. Call `FeedViewModel.addComment`.
2. Save an optional stopwatch duration with the comment.
3. Close the sheet.

When the stopwatch completion event arrives, FeedScreen opens the same sheet with the task's reel and elapsed session duration already associated. Dismissing acknowledges the one-shot event.

---

## 12. Chat Feature

### 12.1 `TalkViewModel`

Extends `AndroidViewModel` for application-level WorkManager access.

#### `send(text)`

1. Reject blank text.
2. Insert a `ChatMessageEntity`.
3. Set `isFromUser=true`.
4. Store creation timestamp.
5. Enqueue batch worker.

**Reason:** The UI remains responsive. The send operation does not wait for AI extraction or reel generation.

### 12.2 `TalkWithMeScreen`

The screen uses `Scaffold` and a top app bar.

Prompt chips:

- Upcoming tasks/deadlines
- Long-term goals
- Motivation
- Peak energy time

Selecting a chip copies it into the text field. It does not automatically send it.

The `Skip prompts` switch hides/shows those optional prompt chips. Its state is local to the screen and is not persisted across navigation/process recreation.

The user can enter at least three text lines and press `Send to background planner`.

On send:

1. ViewModel inserts message.
2. Text field is cleared.
3. The screen's Room-backed message `Flow` displays the inserted user message.

Chat history is loaded in creation-time order. Each message row uses `isFromUser` to choose a Material container color. Assistant-message presentation is reserved but the current fake worker does not insert assistant messages.

---

## 13. Vault Feature

### 13.1 `VaultViewModel`

Exposes two Room Flows:

- Starred
- Liked

#### `unstar(reel)`

Sets starred false and timestamp null.

#### `unlike(reel)`

Sets liked false while leaving star state untouched. This keeps style training and bookmarking independent.

### 13.2 `VaultScreen`

Uses `TabRow` with:

- Starred tab
- Liked tab

Starred reel order:

```text
starredAtEpochMs DESC
```

Liked reel order:

```text
createdAtEpochMs DESC
```

The Starred tab includes `Remove from starred`; the Liked tab includes `Unlike`.

**Gap:** It does not offer the specified search/filter chips or in-vault comment editing.

---

## 14. End-to-End Flows

### 14.1 App Start

```text
InstaTaskApp.onCreate
  -> enqueue InterSessionBatchWorker
MainActivity
  -> feed route
FeedViewModel
  -> subscribe to active reel Flow
InterSessionBatchWorker
  -> no pending input
  -> seed starter reels if reelCount == 0
Feed Flow
  -> refresh VerticalPager
```

### 14.2 User Sends Chat

```text
TalkWithMeScreen
  -> text send
TalkViewModel.send
  -> insert ChatMessageEntity(processed=false)
  -> enqueue batch
InterSessionBatchWorker
  -> read delta
  -> FakeIntelligenceEngine.extractUserDelta
  -> index motivations/comments
  -> upsert tasks/goals/permission reels
  -> mark chat/comment processed
Room Flow
  -> FeedScreen updates
```

### 14.3 User Starts Task

```text
Task Reel Actioniser
  -> FeedViewModel.startTask
  -> explicit service Intent
StopwatchForegroundService
  -> stopwatchState.start
  -> startForeground
Room
  -> unchanged until stop
```

### 14.4 Pause and Resume

```text
Pause
  -> service pause action
  -> accumulatedMs += elapsed since resume
  -> runState PAUSED

Resume
  -> service resume action
  -> lastResumedRealtimeMs = now
  -> runState RUNNING
```

### 14.5 Stop Task

```text
Service stop
  -> StopwatchState.stop
  -> final elapsed calculation
  -> Room addFocusedTime
  -> foreground removal
  -> service stop
```

No reflection comment is opened automatically.

### 14.6 User Adds Reflection

```text
Feed comment button
  -> CommentSheet
  -> addComment
  -> insert CommentEntity(processed=false)
  -> enqueue batch
Worker
  -> read comment
  -> index feedback
  -> run fake extraction
  -> mark comment processed
```

### 14.7 Like vs Star

Like:

```text
Reel.isLiked toggle
  -> future style examples / current count API
  -> not a vault bookmark
```

Star:

```text
Reel.isStarred toggle
  -> starredAtEpochMs set
  -> appears in Vault Starred tab
  -> independent of Like state
```

### 14.8 Web Permission

```text
Permission Reel
  -> scroll past: no permission; no worker
  -> tap tick:
     approvePermission
     -> enqueue ConstrainedWebSearchWorker(query,reelId)
     -> OkHttp request
     -> Jsoup parse snippets
     -> index WEB_SNIPPET
     -> update reel with approval
```

---

## 15. Data Schema

### 15.1 Table Summary

| Table | Primary key | Main role |
|---|---|---|
| `plans` | `planId` | Living multi-step roadmap |
| `tasks` | `taskId` | Actionable work unit |
| `reels` | `reelId` | Feed card/deck |
| `comments` | `commentId` | Reflection and feedback queue |
| `chat_messages` | `messageId` | Brain-dump queue |
| `memory_chunks` | `chunkId` | RAG memory/vector index |

### 15.2 Important Relationships

There are no enforced Room foreign keys.

Relationships are logical:

```text
PlanEntity.planReelId -> ReelEntity.reelId
PlanEntity.planId -> TaskEntity.parentPlanId
TaskEntity.taskId -> ReelEntity.linkedTaskId
PlanEntity.planId -> ReelEntity.linkedPlanId
ReelEntity.reelId -> CommentEntity.reelId
```

**Reason:** The V1 schema favors flexible, low-bloat records and batch mutation. Future reliability improvements would add foreign keys or explicit lifecycle cleanup, plus cascade behavior.

### 15.3 Indexes

Indexes optimize likely queries:

- Task parent plan
- Task status
- Reel active deck
- Reel star state
- Reel plan link
- Comment reel link
- Comment processing flag

Indexes trade write/storage cost for fast feed/vault/worker reads.

---

## 16. Concurrency and State Model

### 16.1 Room and Coroutines

Room suspend functions execute outside UI blocking code. ViewModels launch coroutines through `viewModelScope`.

Room Flows run reactive database observers and emit on relevant table invalidation.

### 16.2 WorkManager

WorkManager is responsible for:

- Batch delta processing
- Starter seeding
- Web lookup
- Unique background scheduling

The app uses `REPLACE`, so rapidly repeated chat/comment actions can replace pending work for the same unique name.

### 16.3 Stopwatch

Stopwatch state is a singleton `StateFlow`.

Time is based on `SystemClock.elapsedRealtime()` for monotonic counting.

Persistence occurs only on stop, not continuously. The foreground notification keeps the process available, but process loss can lose an active in-memory pause state.

---

## 17. Security and Privacy

### 17.1 Local by Default

Current chat, comments, plans, tasks, memory, and embeddings stay in local Room storage.

The fake AI performs no cloud inference.

### 17.2 Explicit Network

The app declares Internet permission but only requests network work after permission-reel approval.

Scrolling past the permission reel does not enqueue a worker.

### 17.3 Data Sent to the Network

The web worker sends only the permission reel query to DuckDuckGo HTML search.

It does not send the whole chat, user ID, local memory, or task database.

### 17.4 Current Network Risks

- DuckDuckGo HTML is a third-party endpoint.
- Search result text is untrusted and should be sanitized before final UI display.
- There is no allowlist or TLS host policy beyond OkHttp defaults.
- HTML parsing does not verify source quality.

---

## 18. Performance and Resource Choices

### 18.1 Lightweight Reels

Reels are Room records with Markdown text and short metadata. There are no synthesized MP4 or reel asset files.

### 18.2 60-Reel Cap

`observeActiveSessionDeck()` limits returned reels to 60.

**Current gap:** Worker insertion is not followed by a cleanup pass that marks extras out of the active deck.

### 18.3 RAG Bound

RAG loads at most 2,000 memory chunks and retrieves only top `k`, default 3.

Each memory text is capped at 300 characters.

This keeps prompt context small but the current in-memory scan may grow linearly.

### 18.4 Batched Inference

All task extraction is intended to run in WorkManager, not UI view models.

UI interactions do not invoke AI synchronously.

### 18.5 Embedding Placeholder

The deterministic 384D embedder avoids native model downloads and enables UI/data testing. It should be replaced for semantic retrieval.

---

## 19. Current Gaps and Required Next Decisions

These are implementation gaps observed in the codebase:

### 19.1 Real AI

Only the fake engine is bound.

Missing work:

- LiteRT-LM wrapper lifecycle
- Single-thread inference ownership
- Model downloader and SHA-256 validation
- Structured JSON parsing and fallback
- Prompt templates
- Real 384D embedder

### 19.2 Media Assets

No six-track `res/raw` set is included.

The audio controller is present but reel/page changes do not call it.

### 19.3 Stopwatch Lifecycle

Missing or incomplete:

- Persistent state across process restart
- Stopwatch pill UI
- Runtime permission UX for notifications on Android versions that require it

### 19.4 Feed Ranking

`FeedRanker` is called by `InterSessionBatchWorker`, and scores are persisted into `sessionOrderRank`.

Remaining limitations:

- There is no deadline timestamp in the task model, so ranking cannot score deadline proximity.
- The worker caps the active deck at 60; it does not target a smaller adaptive count.
- Random jitter is recomputed each batch, so rank ordering can vary between batch runs.

### 19.5 Parent Plan Mutation

Plan completion rewrites the existing Plan Reel and creates the next active child task and TASK reel. The plan mutation and associated inserts occur inside the worker transaction.

### 19.6 Starter Deck

The worker restores stable-ID starter reels whenever the active deck is empty. Existing starter rows are reactivated, preserving saved state.

### 19.7 Desk/Mock AI

The fake engine's keyword completion logic can:

- Mark tasks complete if text contains `"done"` even if unrelated
- Treat every non-completed note as a new task
- Not parse real goals
- Not produce external-facts requirements

### 19.8 Permission UX

The Permission Reel uses a check icon and only enqueues web lookup after the user taps it.

Scrolling past does not maintain or mark a denial. It simply takes no action.

### 19.9 Markdown

The feed uses `multiplatform-markdown-renderer-m3` with a bounded plain-text fallback for invalid control characters, excessively long payloads, and unclosed fenced-code blocks.

Plan formatting is Markdown and the renderer is now integrated.

### 19.10 Database Migration

There are no explicit migrations or schema exports.

### 19.11 Additional V1 limitations

- The deterministic embedding is not semantic and must be replaced before presenting retrieval as intelligent.
- The fake engine uses simple keyword heuristics, does not produce `newGoals`, and can produce false task completions.
- No real LiteRT-LM runtime, model download, or six-track audio bundle is included.
- `MoodAudioController` is not wired into reel page/lifecycle changes.
- Starter TASK reels are sample content and are not linked to TaskEntity rows.
- The `Skip prompts` setting lasts only for the current chat screen instance.
- No assistant/chat reply records are created; message history currently contains user brain dumps.
- The completion event is in-process; process death while timing loses current stopwatch state.
- WorkManager output persistence and processed flags are transactional, but model extraction runs before the transaction. If a batch fails after extraction, the input remains unprocessed and will be extracted again.

---

## 20. Failure Behavior

### 20.1 Batch Worker

If chat/comment data is empty:

- Seeds starter deck if needed.
- Returns success.

If extraction throws:

- CoroutineWorker will usually permit WorkManager retry/error handling according to the exception; no custom `Result.retry()` is written by this worker.

After extraction succeeds, generated entities, RAG writes, starter recovery, rank updates, and processed flags are committed in one Room transaction. A database write failure rolls all those changes back together.

### 20.2 Web Worker

- Missing query -> `Result.failure()`
- HTTP failure -> `Result.retry()`
- No snippets -> `Result.retry()`
- Successful fetch -> success

This gives transient network failures retry semantics and invalid input permanent failure.

### 20.3 Stopwatch

Invalid or unknown service actions return `START_STICKY` without changing state.

Stop persists task focused time and emits a completion event. FeedViewModel resolves the linked task reel and FeedScreen opens the reflection sheet; submitting that comment then enqueues the batch worker.

### 20.4 Feed

Empty deck shows a loading spinner indefinitely until the worker/repository adds a reel.

Blank chat/comment input is ignored locally.

---

## 21. Testing Strategy

Unit tests currently cover the requested pure logic in `core/model`, `core/database`, and `core/worker`.

Recommended test groups:

### Domain tests

- `PlanMarkdownFormatter` step order and status markers
- `ActiveStopwatchState.computeCurrentElapsedMs` for idle, paused, running, and backward-time inputs
- `ReelUiModel.actioniserMode` across all reel types
- `FeedRanker` urgency, time matching, active plan continuity, and time bucket mapping

### Database tests

- Plan JSON conversion
- FloatArray conversion
- Plan advancement
- Star/like separation
- Processed delta flags
- Starter seeding

### Worker tests

- Empty delta seeds starter deck
- Completed task marks completed and deletes task reel
- Plan advancement updates same Plan Reel
- Next task generation
- Web permission worker does not enqueue itself on denial
- Web snippets are capped

### UI tests

- Empty feed
- Vertical page navigation
- Task actioniser starts service intent
- Permission actioniser enqueues web worker
- Star updates vault
- Comment batch enqueue
- Chat blank input rejection

---

## 22. Contributor Decision Guide

### When adding a new reel type

Update:

1. `ReelType`
2. `ReelUiModel.actioniserMode`
3. `FeedScreen` action dispatch
4. `FakeIntelligenceEngine.generateReelMarkdown`
5. `InterSessionBatchWorker` generation
6. Mood mapping

### When adding a plan field

Update:

1. `PlanEntity`
2. `Converters` if the field is not primitive
3. Database migration
4. Plan formatter
5. Advance worker logic
6. Vault/feed UI if visible

### When adding AI behavior

Prefer:

1. Changing `TaskIntelligenceEngine`
2. Implementing a real engine
3. Binding it in `AiModule`

Do not put model-specific classes directly inside feature modules.

### When adding a web source

Keep it behind an approved Permission Reel and return only extracted text, never raw HTML, to RAG storage.

### When changing feed ordering

Update both:

1. `FeedRanker`
2. Worker rank persistence
3. DAO retrieval order

The DAO reads persisted rank descending. When extending ranking, update `FeedRanker`, its worker inputs, persistence, and its deterministic unit tests together.

---

## 23. Build Verification

Verified command:

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

Verified outcome:

- Both build and unit-test tasks successful
- Debug APK generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The build uses `FakeIntelligenceEngine` and does not download or integrate a LiteRT-LM model.
