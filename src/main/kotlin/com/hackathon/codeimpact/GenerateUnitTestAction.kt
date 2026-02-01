package com.hackathon.codeimpact

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

class GenerateUnitTestAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // 1. Get method under cursor
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)

        if (method == null) {
            Messages.showInfoMessage("Please click inside a method definition first.", "No Method Found")
            return
        }

        val methodName = method.name
        val className = method.containingClass?.name ?: "Unknown"

        // 2. Get the Package Name from the Source File
        val packageName = (psiFile as? com.intellij.psi.PsiJavaFile)?.packageName ?: ""
        val packageDeclaration = if (packageName.isNotEmpty()) "package $packageName;" else ""

        // 3. Formatting
        val capMethodName = methodName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // 4. The JUnit Content (Now with correct Package!)
        val testContent = """
$packageDeclaration

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertTrue;

public class ${className}Test {

    @Test
    public void test${capMethodName}Performance() {
        System.out.println("--- 🧪 Starting AI Verification Test ---");
        
        // 1. Setup
        ${className} service = new ${className}();
        List<${className}.User> users = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            users.add(new ${className}.User("User" + i, i));
        }

        // 2. Execute
        long start = System.currentTimeMillis();
        service.generateReport(users);
        long end = System.currentTimeMillis();
        long duration = end - start;

        System.out.println("Execution Time: " + duration + "ms");

        // 3. Assert (JUnit 4)
        assertTrue("Performance Requirement Failed! Took " + duration + "ms", duration < 100);
        
        System.out.println("--- ✅ Test Passed: Optimization Verified ---");
    }
}
        """.trimIndent()

        // 5. Create File Safely in Same Directory
        createTestFileInSameDirectory(project, psiFile, "${className}Test.java", testContent)
    }

    private fun createTestFileInSameDirectory(project: Project, sourceFile: com.intellij.psi.PsiFile, fileName: String, content: String) {
        val directory = sourceFile.containingDirectory

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                // Check if file exists
                val existingFile = directory.findFile(fileName)

                if (existingFile != null) {
                    FileEditorManager.getInstance(project).openFile(existingFile.virtualFile, true)
                } else {
                    // Create new file using PSI (Native IntelliJ API)
                    val factory = PsiFileFactory.getInstance(project)
                    val newFile = factory.createFileFromText(fileName, JavaFileType.INSTANCE, content)

                    // Add to directory
                    val addedFile = directory.add(newFile)

                    // Open it
                    val virtualFile = (addedFile as com.intellij.psi.PsiFile).virtualFile
                    FileEditorManager.getInstance(project).openFile(virtualFile, true)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}