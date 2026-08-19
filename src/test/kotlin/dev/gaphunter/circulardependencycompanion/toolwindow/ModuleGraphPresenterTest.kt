package dev.gaphunter.circulardependencycompanion.toolwindow

import dev.gaphunter.circulardependencycompanion.graph.CycleDetector
import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import dev.gaphunter.circulardependencycompanion.model.ModuleEdge
import dev.gaphunter.circulardependencycompanion.model.ModuleGraph
import dev.gaphunter.circulardependencycompanion.model.ModuleNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleGraphPresenterTest {

    @Test
    fun `layers a simple DAG by dependency depth`() {
        val graph = ModuleGraph(
            BuildSystem.GRADLE,
            nodes = listOf(ModuleNode(":app"), ModuleNode(":core")),
            edges = listOf(ModuleEdge(":app", ":core")),
        )

        val view = ModuleGraphPresenter.buildLayeredView(graph, CycleDetector.findCycles(graph))

        assertEquals(2, view.layers.size)
        assertEquals(listOf(":core"), view.layers[0].map { it.name })
        assertEquals(listOf(":app"), view.layers[1].map { it.name })
        assertTrue(view.moduleNamesInCycles.isEmpty())
    }

    @Test
    fun `modules in a cycle are all marked and placed in layer 0`() {
        val graph = ModuleGraph(
            BuildSystem.GRADLE,
            nodes = listOf(ModuleNode(":a"), ModuleNode(":b"), ModuleNode(":c")),
            edges = listOf(ModuleEdge(":a", ":b"), ModuleEdge(":b", ":c"), ModuleEdge(":c", ":a")),
        )
        val cycles = CycleDetector.findCycles(graph)

        val view = ModuleGraphPresenter.buildLayeredView(graph, cycles)

        assertEquals(setOf(":a", ":b", ":c"), view.moduleNamesInCycles)
        assertEquals(1, view.layers.size)
        assertEquals(3, view.layers[0].size)
    }

    @Test
    fun `a diamond dependency does not duplicate the shared module across layers`() {
        val graph = ModuleGraph(
            BuildSystem.GRADLE,
            nodes = listOf(ModuleNode(":app"), ModuleNode(":a"), ModuleNode(":b"), ModuleNode(":shared")),
            edges = listOf(
                ModuleEdge(":app", ":a"),
                ModuleEdge(":app", ":b"),
                ModuleEdge(":a", ":shared"),
                ModuleEdge(":b", ":shared"),
            ),
        )

        val view = ModuleGraphPresenter.buildLayeredView(graph, emptyList())

        val allNames = view.layers.flatMap { layer -> layer.map { it.name } }
        assertEquals(listOf(":app", ":a", ":b", ":shared").sorted(), allNames.sorted())
        assertEquals(1, allNames.count { it == ":shared" })
    }
}
