package dev.gaphunter.circulardependencycompanion.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CircularDependencyToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CircularDependencyToolWindow(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
