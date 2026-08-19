package dev.gaphunter.circulardependencycompanion.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import dev.gaphunter.circulardependencycompanion.graph.ProjectAnalysisResult
import dev.gaphunter.circulardependencycompanion.graph.ProjectGraphAnalyzer
import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.io.File
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * The "Module Dependencies" tool window: builds and displays the
 * module dependency graph for the currently open project ([analyze]),
 * with real cycles (if any) called out in their own top section --
 * see [ModuleGraphPresenter] for why this renders as a layered,
 * indented tree instead of a hand-drawn graphical diagram.
 *
 * Manual refresh via a toolbar action, not automatic on every file
 * keystroke: walking every module's build file on each edit would be
 * wasted work for a graph that only meaningfully changes when a
 * dependency declaration is added/removed/edited and saved -- the
 * refresh action itself already runs off the EDT (see [runAnalysis]),
 * same "heavy computation off the EDT, `invokeLater` for the UI
 * update" discipline as every other plugin in this catalog
 * (`CONSTITUTION.md` §6), so a manual trigger costs nothing extra to
 * add.
 */
class CircularDependencyToolWindow(private val project: Project, toolWindow: ToolWindow) {

    val component: JPanel = JPanel(BorderLayout())

    private val rootNode = DefaultMutableTreeNode("No analysis run yet")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    init {
        component.border = JBUI.Borders.empty(4)
        tree.isRootVisible = true
        tree.cellRenderer = CycleHighlightingCellRenderer()
        component.add(buildToolbar(toolWindow).component, BorderLayout.NORTH)
        component.add(JScrollPane(tree), BorderLayout.CENTER)
        runAnalysis()
    }

    private fun buildToolbar(toolWindow: ToolWindow): ActionToolbar {
        val refreshAction = object : AnAction("Refresh", "Re-scan the project's build files", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = runAnalysis()
        }
        val group = DefaultActionGroup(refreshAction)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        toolbar.targetComponent = component
        return toolbar
    }

    private fun runAnalysis() {
        val basePath = project.basePath
        if (basePath == null) {
            showEmptyState("No project directory found")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                ProjectGraphAnalyzer.analyze(File(basePath))
            } catch (_: Exception) {
                // A single unreadable/malformed build file is already
                // swallowed inside each parser -- this is the last-resort
                // guard so the tool window itself never shows a stack
                // trace instead of an honest empty/error state.
                ProjectAnalysisResult.EMPTY
            }
            SwingUtilities.invokeLater { render(result) }
        }
    }

    private fun render(result: ProjectAnalysisResult) {
        if (result.graph.buildSystem == BuildSystem.NONE) {
            showEmptyState(
                "No cycles possible: single-module project (no Gradle " +
                    "settings.gradle(.kts) include(...) or Maven <modules> " +
                    "found with more than one module).",
            )
            return
        }

        val view = ModuleGraphPresenter.buildLayeredView(result.graph, result.cycles)
        rootNode.removeAllChildren()
        rootNode.userObject = buildSystemLabel(result.graph.buildSystem)

        if (view.cycles.isNotEmpty()) {
            val cyclesNode = CycleSectionNode("Cycles detected (${view.cycles.size})")
            for (cycle in view.cycles) {
                val chain = cycle.path.joinToString(" -> ") + " -> " + cycle.path.first()
                cyclesNode.add(DefaultMutableTreeNode(CycleEntryLabel(chain)))
            }
            rootNode.add(cyclesNode)
        } else {
            rootNode.add(DefaultMutableTreeNode("No cycles detected"))
        }

        val layersNode = DefaultMutableTreeNode("Modules (${view.layers.sumOf { it.size }}), by dependency layer")
        view.layers.forEachIndexed { depth, entries ->
            val layerNode = DefaultMutableTreeNode("Layer $depth")
            for (entry in entries) {
                val label = if (entry.dependsOn.isEmpty()) {
                    entry.name
                } else {
                    "${entry.name}  ->  ${entry.dependsOn.joinToString(", ")}"
                }
                val moduleLabel = if (entry.name in view.moduleNamesInCycles) CycleEntryLabel(label) else label
                layerNode.add(DefaultMutableTreeNode(moduleLabel))
            }
            layersNode.add(layerNode)
        }
        rootNode.add(layersNode)

        treeModel.reload()
        for (i in 0 until tree.rowCount) tree.expandRow(i)
    }

    private fun showEmptyState(message: String) {
        rootNode.removeAllChildren()
        rootNode.userObject = message
        treeModel.reload()
    }

    private fun buildSystemLabel(buildSystem: BuildSystem): String = when (buildSystem) {
        BuildSystem.GRADLE -> "Gradle multi-module project"
        BuildSystem.MAVEN -> "Maven multi-module project"
        BuildSystem.NONE -> "No build system detected"
    }
}

/** Marker tree node so [CycleHighlightingCellRenderer] can style the "Cycles detected" section header distinctly, without string-matching the label text. */
private class CycleSectionNode(text: String) : DefaultMutableTreeNode(text)

/** Wraps a leaf's display text to mark it as cycle-related for [CycleHighlightingCellRenderer], without changing [DefaultMutableTreeNode.toString] callers rely on elsewhere. */
private data class CycleEntryLabel(val text: String) {
    override fun toString(): String = text
}

/** Renders cycle-related nodes (the "Cycles detected" header and every module/path entry inside it or flagged elsewhere) in red -- the one visual distinction the task brief asks for ("color distinto"). */
private class CycleHighlightingCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = value as? DefaultMutableTreeNode
        val isCycleRelated = node is CycleSectionNode ||
            (node?.parent is CycleSectionNode) ||
            node?.userObject is CycleEntryLabel
        if (isCycleRelated && !selected) {
            foreground = CYCLE_COLOR
        }
        return component
    }

    companion object {
        private val CYCLE_COLOR: Color = JBColor(0xC7222D, 0xFF6B68)
    }
}
