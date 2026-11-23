package com.instatask.core.database

import com.instatask.core.model.PlanStep
import com.instatask.core.model.StepStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanMarkdownFormatterTest {
    @Test
    fun rendersStepsInIndexOrderWithStateMarkers() {
        val markdown = PlanMarkdownFormatter.renderPlanReel(
            "Exam preparation",
            listOf(
                PlanStep(2, "Review notes", StepStatus.PENDING),
                PlanStep(0, "Read chapter", StepStatus.COMPLETED),
                PlanStep(1, "Solve questions", StepStatus.CURRENT),
            ),
        )

        assertEquals(
            """
                ## Exam preparation

                - ✅ ~~Read chapter~~
                - ➡️ Solve questions
                - ⬜ Review notes
            """.trimIndent(),
            markdown,
        )
    }
}
