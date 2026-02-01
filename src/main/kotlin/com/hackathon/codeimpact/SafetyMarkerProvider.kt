package com.hackathon.codeimpact

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import javax.swing.Icon

class SafetyMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element is PsiIdentifier && element.parent is PsiMethod) {
            val method = element.parent as PsiMethod

            // 1. Filter: Ignore empty methods or constructors without params
            if (method.parameterList.parametersCount == 0) return
            if (method.isConstructor) return

            // 2. Filter: Don't show if already protected (Simple heuristic)
            val bodyText = method.body?.text ?: ""
            if (bodyText.contains("IllegalArgumentException")) return

            // 3. Add SAFETY Marker
            // We use 'Warning' because the method is currently UNGUARDED (Risky)
            val shieldMarker = RelatedItemLineMarkerInfo(
                element,
                element.textRange,
                AllIcons.General.Warning, // <--- CHANGED FROM SHIELD TO WARNING
                { "⚠️ Unsafe Method: Click to Auto-Generate Guards" },
                { _, _ -> applyGuards(method) },
                GutterIconRenderer.Alignment.LEFT,
                { emptyList() }
            )
            result.add(shieldMarker)
        }
    }

    private fun applyGuards(method: PsiMethod) {
        val project = method.project
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(method.containingFile) ?: return

        val guardCode = SafetyService.generateGuards(method)
        if (guardCode.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val body = method.body
            if (body != null) {
                // Insert after the opening "{"
                val startOffset = body.lBrace?.textRange?.endOffset ?: body.textRange.startOffset + 1
                document.insertString(startOffset, "\n" + guardCode)
                documentManager.commitDocument(document)
            }
        }
    }
}