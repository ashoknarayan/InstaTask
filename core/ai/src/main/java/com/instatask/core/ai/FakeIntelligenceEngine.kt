package com.instatask.core.ai

import com.instatask.core.model.ReelType
import com.instatask.core.model.TimeBucket
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeIntelligenceEngine @Inject constructor() : TaskIntelligenceEngine {
    override suspend fun isModelReady(): Boolean = true

    override suspend fun extractUserDelta(
        rawNotesAndComments: List<String>,
        activeTasks: List<Pair<String, String>>,
    ): ExtractedDelta {
        delay(150)
        val joined = rawNotesAndComments.joinToString(" ").lowercase()
        val completedIds = activeTasks
            .filter { (_, title) ->
                joined.contains("done") || joined.contains("completed") ||
                    joined.contains(title.lowercase())
            }
            .map { it.first }

        val newTasks = if (rawNotesAndComments.isNotEmpty() && completedIds.isEmpty()) {
            listOf(
                ExtractedTaskDraft(
                    title = rawNotesAndComments.last().take(60),
                    timeBucket = when {
                        joined.contains("morning") -> TimeBucket.MORNING
                        joined.contains("afternoon") -> TimeBucket.AFTERNOON
                        joined.contains("evening") -> TimeBucket.EVENING
                        joined.contains("night") -> TimeBucket.NIGHT
                        else -> TimeBucket.ANY
                    },
                    isUrgentTimeCrunch = joined.contains("tomorrow") ||
                        joined.contains("urgent") || joined.contains("deadline"),
                ),
            )
        } else emptyList()

        val goalPhrase = rawNotesAndComments.lastOrNull { it.contains("goal", true) }
        val motivations = if (joined.contains("because") || goalPhrase != null) {
            listOf(goalPhrase ?: rawNotesAndComments.lastOrNull().orEmpty())
        } else emptyList()

        val needsWeb = when {
            joined.contains("learn") -> "how to " + rawNotesAndComments.last().take(80)
            joined.contains("research") -> rawNotesAndComments.last().take(80)
            else -> null
        }

        return ExtractedDelta(
            newTasks = newTasks,
            completedTaskIds = completedIds,
            coreMotivations = motivations.filter(String::isNotBlank),
            needsWebLookupQuery = needsWeb,
        )
    }

    override suspend fun generateReelMarkdown(
        reelType: ReelType,
        subjectTitle: String,
        ragContextSnippets: List<String>,
        likedStyleExamples: List<String>,
    ): String = when (reelType) {
        ReelType.TASK -> """
            ## Focus Block: $subjectTitle
            - **Action:** Tap the stopwatch on the right rail.
            - **Rule:** Zero distractions until you log your reflection.
        """.trimIndent()
        ReelType.PLAN -> """
            ## Roadmap: $subjectTitle
            - ➡️ Start Step 1 today
            - ⬜ Build momentum in focused blocks
        """.trimIndent()
        ReelType.MOTIVATIONAL -> """
            ## You've Handled Harder Than This
            **$subjectTitle** is one focused sprint away.
            ${ragContextSnippets.firstOrNull()?.let { "> $it" }.orEmpty()}
        """.trimIndent()
        ReelType.PERMISSION -> """
            ## External Research Request
            Allow InstaTask to search the web once for **$subjectTitle**?
            - Tap ✓ to approve
            - Scroll past to decline
        """.trimIndent()
        ReelType.INFO -> """
            ## Quick Insight: $subjectTitle
            ${ragContextSnippets.firstOrNull() ?: "Consistency compounds faster than intensity."}
        """.trimIndent()
    }
}
