package com.instatask.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReelType { TASK, PLAN, MOTIVATIONAL, PERMISSION, INFO }

@Serializable
enum class MoodCategory { MOTIVATIONAL, PEACEFUL, ENERGETIC, SILENT }

enum class ActioniserMode { STOPWATCH, PERMISSION_TICK, STAR }

@Serializable
enum class TimeBucket { MORNING, AFTERNOON, EVENING, NIGHT, ANY }

@Serializable
enum class StepStatus { COMPLETED, CURRENT, PENDING }

@Serializable
enum class PlanStatus { ACTIVE, COMPLETED, ARCHIVED }

@Serializable
enum class TaskStatus { ACTIVE, COMPLETED }

@Serializable
enum class MemorySource { CHAT_DUMP, ONBOARDING_WHY, COMPLETED_WIN, COMMENT_FEEDBACK, WEB_SNIPPET }

enum class StopwatchRunState { IDLE, RUNNING, PAUSED }
