package com.hackathon.codeimpact

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class ImpactToolWindowFactory : ToolWindowFactory {

    companion object {
        private var browser: JBCefBrowser? = null

        fun updateContent(result: AIService.AnalysisResult, element: Any?) {
            if (browser == null) return

            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <script type="module">
                        import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
                        mermaid.initialize({ 
                            startOnLoad: true, 
                            theme: 'base', 
                            themeVariables: { 
                                darkMode: true, 
                                background: '#2b2b2b', 
                                primaryColor: '#3c3f41', 
                                lineColor: '#a9b7c6'
                            } 
                        });
                    </script>
                    <style>
                        :root {
                            --bg-color: #1e1e1e;
                            --card-bg: #2b2b2b;
                            --text-primary: #e0e0e0;
                            --text-secondary: #9aa0a6;
                            --border-color: #3e3e3e;
                        }

                        body { 
                            background-color: var(--bg-color); 
                            color: var(--text-primary); 
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; 
                            margin: 0;
                            padding: 20px;
                        }

                        h2 { 
                            font-size: 11px; 
                            text-transform: uppercase;
                            letter-spacing: 1.2px;
                            color: var(--text-secondary); 
                            margin-bottom: 12px;
                            margin-top: 25px;
                            font-weight: 700;
                            border-bottom: 1px solid var(--border-color);
                            padding-bottom: 5px;
                        }
                        
                        h2:first-child { margin-top: 0; }

                        /* GRAPH CARD */
                        .graph-card {
                            background: var(--card-bg);
                            border: 1px solid var(--border-color);
                            border-radius: 6px;
                            padding: 10px;
                            display: flex;
                            justify-content: center;
                            box-shadow: 0 4px 10px rgba(0,0,0,0.3);
                        }

                        /* METRICS GRID */
                        .metrics-grid {
                            display: grid;
                            grid-template-columns: 1fr;
                            gap: 10px;
                        }

                        .metric-card { 
                            background: var(--card-bg); 
                            border: 1px solid var(--border-color);
                            border-radius: 6px; 
                            padding: 12px 15px; 
                            display: flex; 
                            flex-direction: row;
                            justify-content: space-between;
                            align-items: center;
                            transition: background-color 0.2s;
                        }
                        
                        .metric-card:hover {
                            background-color: #333;
                        }
                        
                        .metric-label { 
                            font-size: 12px;
                            color: var(--text-secondary);
                            font-weight: 500;
                        }
                        
                        .metric-value {
                            font-size: 14px;
                            font-weight: 600;
                        }
                    </style>
                </head>
                <body>
                    <h2>System Architecture</h2>
                    <div class="graph-card">
                        <div class="mermaid">
                            ${result.graph}
                        </div>
                    </div>

                    <h2>Performance Analysis</h2>
                    <div class="metrics-grid">
                        <div class="metric-card">
                            <span class="metric-label">Latency</span>
                            <span class="metric-value">${result.latency}</span>
                        </div>
                        <div class="metric-card">
                            <span class="metric-label">Architecture</span>
                            <span class="metric-value">${result.savings}</span>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            browser?.loadHTML(htmlContent)
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        try {
            browser = JBCefBrowser()
            panel.add(browser!!.component, BorderLayout.CENTER)
            val initialResult = AIService.AnalysisResult(
                "graph TD; Ready[System Ready]-->Analyze[Select Method]; style Ready fill:#2b2b2b,stroke:#555; style Analyze fill:#2b2b2b,stroke:#555;",
                "", "", "...", "...", emptyList()
            )
            updateContent(initialResult, null)
        } catch (e: Exception) {
            panel.add(JLabel("JCEF Browser not supported", SwingConstants.CENTER))
        }

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}