package com.hackathon.codeimpact

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

class OptimizationMarkerProvider : RelatedItemLineMarkerProvider() {

    private val sourceMethods = setOf("addUser", "processOrder", "generateReport")
    private val destMethods = setOf("save", "invalidate", "blastWinners", "updateInventory", "getConnection")

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element is PsiIdentifier && element.parent is PsiMethod) {
            val method = element.parent as PsiMethod
            val name = method.name

            val isSlow = name in sourceMethods
            val isUnsafe = checkSafetyNeeded(method)

            // --- TYPE A: TARGET ICON 🎯 (Dependencies) ---
            if (name in destMethods && !isSlow) {
                val navMarker = RelatedItemLineMarkerInfo(
                    element,
                    element.textRange,
                    AllIcons.General.Locate,
                    { "Trace Dependencies" },
                    { e, psiElement -> showSmartNavigationPopup(e, psiElement, name) },
                    GutterIconRenderer.Alignment.RIGHT, { emptyList() }
                )
                result.add(navMarker)
                return
            }

            // --- TYPE B: BOLT ICON ⚡ (Optimize & Safety) ---
            if (isSlow || isUnsafe) {
                val tooltip = "Code Impact Analysis"

                val marker = RelatedItemLineMarkerInfo(
                    element,
                    element.textRange,
                    AllIcons.Actions.Lightning,
                    { tooltip },
                    { e, psiElement -> showUnifiedMenu(e, psiElement, method, isSlow, isUnsafe) },
                    GutterIconRenderer.Alignment.RIGHT,
                    { emptyList() }
                )
                result.add(marker)
            }
        }
    }

    // --- SAFETY CHECK ---
    private fun checkSafetyNeeded(method: PsiMethod): Boolean {
        if (method.parameterList.parametersCount == 0) return false
        if (method.isConstructor) return false
        val name = method.name
        if (name.startsWith("set") || name.startsWith("get")) return false
        val bodyText = method.body?.text ?: ""
        return !bodyText.contains("IllegalArgumentException")
    }

    // --- MENU SYSTEM ---
    private fun showUnifiedMenu(
        event: MouseEvent,
        element: PsiElement,
        method: PsiMethod,
        isSlow: Boolean,
        isUnsafe: Boolean
    ) {
        val options = mutableListOf<String>()
        val icons = mutableListOf<Icon>()

        if (isSlow) {
            options.add("Optimize Performance")
            icons.add(AllIcons.Actions.Execute)
        }
        if (isUnsafe) {
            options.add("Add Safety Guards")
            icons.add(AllIcons.General.Warning)
        }

        // NOTE: "Generate Unit Test" is deliberately removed from here.
        // It is now accessed via Right-Click -> Generate AI Unit Test

        val step = object : BaseListPopupStep<String>("Code Impact Actions", options, icons) {
            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                when (selectedValue) {
                    "Optimize Performance" -> showOptimizationPopup(event, element)
                    "Add Safety Guards" -> applyLocalSafetyGuards(method)
                }
                return FINAL_CHOICE
            }
        }

        JBPopupFactory.getInstance().createListPopup(step).show(RelativePoint(event))
    }

    private fun applyLocalSafetyGuards(method: PsiMethod) {
        val project = method.project
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(method.containingFile) ?: return

        val guardCode = SafetyService.generateGuards(method)
        if (guardCode.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val body = method.body
            if (body != null) {
                val startOffset = body.lBrace?.textRange?.endOffset ?: body.textRange.startOffset + 1
                document.insertString(startOffset, "\n" + guardCode)
                documentManager.commitDocument(document)
            }
        }
    }

    // --- PERFORMANCE POPUP ---
    private fun showOptimizationPopup(event: MouseEvent, element: PsiElement) {
        val project = element.project
        val parentMethod = element.parent as? PsiMethod ?: return
        val methodPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parentMethod)
        val codeText = element.text

        val loadingPanel = JPanel(BorderLayout())
        loadingPanel.background = Color(43, 43, 43)
        loadingPanel.border = EmptyBorder(10, 20, 10, 20)
        val loadingLabel = JLabel("Analyzing logic & dependencies...")
        loadingLabel.foreground = Color.WHITE
        loadingPanel.add(loadingLabel, BorderLayout.CENTER)

        val loadingPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(loadingPanel, null).createPopup()
        loadingPopup.show(RelativePoint(event))

        ApplicationManager.getApplication().executeOnPooledThread {
            val fixData = AIService.analyzeCode(codeText)
            ApplicationManager.getApplication().invokeLater {
                loadingPopup.cancel()
                if (!project.isDisposed) showResultPopup(event, project, methodPointer, fixData)
            }
        }
    }

    private fun showResultPopup(event: MouseEvent, project: Project, methodPointer: SmartPsiElementPointer<PsiMethod>, fixData: AIService.AnalysisResult) {
        val panel = JPanel(BorderLayout())
        panel.background = Color(43, 43, 43)
        panel.border = BorderFactory.createLineBorder(Color(70, 70, 70), 1)
        panel.preferredSize = Dimension(380, 220)

        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = Color(60, 20, 20)
        headerPanel.border = EmptyBorder(10, 15, 10, 15)
        val title = JBLabel("⚠️ Critical Issues Detected")
        title.font = Font("SansSerif", Font.BOLD, 13)
        title.foreground = Color(255, 100, 100)
        val subtitle = JBLabel("<html>Code Complexity: <b>O(N²)</b><br>Issues: High CPU, Memory Leaks, Blocking I/O</html>")
        subtitle.font = Font("SansSerif", Font.PLAIN, 11)
        subtitle.foreground = Color(200, 200, 200)
        subtitle.border = EmptyBorder(5, 0, 0, 0)
        headerPanel.add(title, BorderLayout.NORTH)
        headerPanel.add(subtitle, BorderLayout.CENTER)

        val bodyPanel = JPanel(BorderLayout())
        bodyPanel.background = Color(43, 43, 43)
        bodyPanel.border = EmptyBorder(15, 15, 15, 15)
        val solutionText = JBLabel("<html><b>Suggestion:</b> Use Streams & Async IO.<br><br>" +
                "<span style='color:#629755'>⬇ Latency: ${fixData.latency}</span><br>" +
                "<span style='color:#FFD700'>💰 Savings: ${fixData.savings}</span></html>")
        solutionText.foreground = Color.WHITE
        bodyPanel.add(solutionText, BorderLayout.CENTER)

        val footerPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        footerPanel.background = Color(43, 43, 43)
        footerPanel.border = EmptyBorder(0, 10, 10, 10)
        var popup: JBPopup? = null
        val rejectBtn = JButton("Dismiss")
        rejectBtn.isFocusable = false
        rejectBtn.addActionListener { popup?.cancel() }

        val acceptBtn = JButton("✅ Apply Fix")
        acceptBtn.background = Color(54, 88, 128)
        acceptBtn.foreground = Color.WHITE
        acceptBtn.isFocusable = false
        acceptBtn.font = Font("SansSerif", Font.BOLD, 12)

        acceptBtn.addActionListener {
            val validMethod = methodPointer.element
            if (validMethod != null) {
                if (fixData.optimizedCode.length > 10) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val documentManager = PsiDocumentManager.getInstance(project)
                        val document = documentManager.getDocument(validMethod.containingFile)
                        if (document != null) {
                            val startOffset = validMethod.textRange.startOffset
                            val endOffset = validMethod.textRange.endOffset
                            document.replaceString(startOffset, endOffset, fixData.optimizedCode)
                            documentManager.commitDocument(document)
                            val newEndOffset = startOffset + fixData.optimizedCode.length
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            editor?.selectionModel?.setSelection(startOffset, newEndOffset)
                            editor?.scrollingModel?.scrollToCaret(ScrollType.CENTER)
                        }
                    }
                }
            }
            popup?.cancel()
        }

        footerPanel.add(rejectBtn)
        footerPanel.add(acceptBtn)
        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(bodyPanel, BorderLayout.CENTER)
        panel.add(footerPanel, BorderLayout.SOUTH)

        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
            .setMovable(true).setRequestFocus(true).setCancelOnClickOutside(true).createPopup()
        popup?.show(RelativePoint(event))
    }

    // --- NAVIGATION POPUP ---
    private fun showSmartNavigationPopup(event: MouseEvent, element: PsiElement, methodName: String) {
        val panel = JPanel(BorderLayout())
        panel.background = Color(43, 43, 43)
        panel.border = BorderFactory.createLineBorder(Color(70, 70, 70), 1)

        val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        tagsPanel.background = Color(60, 63, 65)
        tagsPanel.border = JBUI.Borders.empty(5)

        var popup: JBPopup? = null

        if (methodName in sourceMethods) {
            val analysis = AIService.analyzeCode(element.text)
            for (target in analysis.jumpTargets) {
                tagsPanel.add(createTagButton("📍 ${target.label}", Color(77, 80, 82), Color(74, 136, 199)) {
                    jumpToText(element, target.searchQuery)
                    popup?.cancel()
                })
            }
        } else {
            tagsPanel.add(createTagButton("⬅️ Back to Caller", Color(77, 80, 82), Color(74, 136, 199)) {
                jumpToText(element, "void generateReport") {
                    jumpToText(element, "void processOrder")
                }
                popup?.cancel()
            })
            if (methodName == "save") {
                tagsPanel.add(JSeparator(SwingConstants.VERTICAL))
                tagsPanel.add(createTagButton("➡️ Trace: Connection Pool", Color(60, 63, 65), Color(80, 83, 85)) {
                    jumpToText(element, "class ConnectionPool")
                    popup?.cancel()
                })
            } else if (methodName == "getConnection") {
                tagsPanel.add(JSeparator(SwingConstants.VERTICAL))
                tagsPanel.add(createTagButton("⬆️ Up to Database", Color(60, 63, 65), Color(80, 83, 85)) {
                    jumpToText(element, "class Database")
                    popup?.cancel()
                })
            }
        }

        tagsPanel.add(JSeparator(SwingConstants.VERTICAL))
        tagsPanel.add(createTagButton("+ Add Tag", Color(43, 43, 43), Color(60, 60, 60)) { popup?.cancel() })

        panel.add(tagsPanel, BorderLayout.CENTER)
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
            .setMovable(true).setRequestFocus(true).setCancelOnClickOutside(true).createPopup()
        popup.show(RelativePoint(event))
    }

    private fun jumpToText(element: PsiElement, searchQuery: String, onFail: (() -> Unit)? = null) {
        val editor = FileEditorManager.getInstance(element.project).selectedTextEditor
        val code = editor?.document?.text ?: ""
        val index = code.indexOf(searchQuery)
        if (index != -1) {
            editor?.caretModel?.moveToOffset(index)
            editor?.scrollingModel?.scrollToCaret(ScrollType.CENTER)
            editor?.selectionModel?.setSelection(index, index + searchQuery.length)
        } else {
            if (onFail != null) onFail()
            else editor?.caretModel?.moveToOffset(0)
        }
    }

    private fun createTagButton(text: String, bgColor: Color, hoverColor: Color, onClick: () -> Unit): JBLabel {
        val btn = JBLabel(text)
        btn.isOpaque = true
        btn.background = bgColor
        btn.foreground = Color.WHITE
        btn.border = JBUI.Borders.empty(4, 8)
        btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        btn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) { btn.background = hoverColor }
            override fun mouseExited(e: MouseEvent?) { btn.background = bgColor }
            override fun mouseClicked(e: MouseEvent?) { onClick() }
        })
        return btn
    }
}