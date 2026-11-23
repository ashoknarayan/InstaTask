package com.instatask.core.ai

import com.instatask.core.model.ReelType
import com.instatask.core.model.TimeBucket
import kotlinx.serialization.Serializable

@Serializable
data class ExtractedDelta(
    val newTasks: List<ExtractedTaskDraft> = emptyList(),
    val newGoals: List<ExtractedGoalDraft> = emptyList(),
    val completedTaskIds: List<String> = emptyList(),
    val coreMotivations: List<String> = emptyList(),
    val needsWebLookupQuery: String? = null,
)

@Serializable
data class ExtractedTaskDraft(
    val title: String,
    val timeBucket: TimeBucket = TimeBucket.ANY,
    val isUrgentTimeCrunch: Boolean = false,
    val isRecurringDaily: Boolean = false,
)

@Serializable
data class ExtractedGoalDraft(
    val goalTitle: String,
    val orderedSteps: List<String>,
    val requiresExternalFacts: Boolean = false,
    val suggestedSearchQuery: String? = null,
)

interface TaskIntelligenceEngine {
    suspend fun isModelReady(): Boolean
    suspend fun extractUserDelta(
        rawNotesAndComments: List<String>,
        activeTasks: List<Pair<String, String>>,
    ): ExtractedDelta
    suspend fun generateReelMarkdown(
        reelType: ReelType,
        subjectTitle: String,
        ragContextSnippets: List<String>,
        likedStyleExamples: List<String>,
    ): String
}
