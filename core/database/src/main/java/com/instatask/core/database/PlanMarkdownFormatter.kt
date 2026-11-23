package com.instatask.core.database

import com.instatask.core.model.PlanStep
import com.instatask.core.model.StepStatus

object PlanMarkdownFormatter {
    fun renderPlanReel(goalTitle: String, steps: List<PlanStep>): String = buildString {
        appendLine("## $goalTitle")
        appendLine()
        steps.sortedBy { it.index }.forEach { step ->
            val marker = when (step.status) {
                StepStatus.COMPLETED -> "✅"
                StepStatus.CURRENT -> "➡️"
                StepStatus.PENDING -> "⬜"
            }
            val text = if (step.status == StepStatus.COMPLETED) "~~${step.title}~~" else step.title
            appendLine("- $marker $text")
        }
    }.trim()
}
