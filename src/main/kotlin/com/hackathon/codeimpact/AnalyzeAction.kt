package com.hackathon.codeimpact

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager

class AnalyzeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeImpact")
        toolWindow?.show()

        // Show loading state (Updated to match full data class)
        ImpactToolWindowFactory.updateContent(
            AIService.AnalysisResult(
                "graph TD; Loading[AI Analysis In Progress...]:::blink;",
                "Contacting OpenRouter AI...",
                "Generating Tests...",
                "...",
                "...",
                emptyList()
            ),
            null
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = AIService.analyzeCode(selectedText)
            ApplicationManager.getApplication().invokeLater {
                ImpactToolWindowFactory.updateContent(result, null)
            }
        }
    }
}