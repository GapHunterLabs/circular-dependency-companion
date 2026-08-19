package dev.gaphunter.circulardependencycompanion.graph

import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import dev.gaphunter.circulardependencycompanion.model.ModuleEdge
import dev.gaphunter.circulardependencycompanion.model.ModuleGraph
import dev.gaphunter.circulardependencycompanion.model.ModuleNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleDetectorTest {

    private fun graphOf(nodeNames: List<String>, edgePairs: List<Pair<String, String>>): ModuleGraph =
        ModuleGraph(
            buildSystem = BuildSystem.GRADLE,
            nodes = nodeNames.map { ModuleNode(it) },
            edges = edgePairs.map { ModuleEdge(it.first, it.second) },
        )

    @Test
    fun `no cycles in a simple DAG`() {
        // app -> core, app -> util, core -> util (a diamond -- must NOT be
        // flagged as a cycle just because util is reached twice).
        val graph = graphOf(
            listOf("app", "core", "util"),
            listOf("app" to "core", "app" to "util", "core" to "util"),
        )

        assertEquals(emptyList<Cycle>(), CycleDetector.findCycles(graph))
    }

    @Test
    fun `detects a 2-module cycle`() {
        val graph = graphOf(listOf("a", "b"), listOf("a" to "b", "b" to "a"))

        val cycles = CycleDetector.findCycles(graph)
        assertEquals(1, cycles.size)
        assertEquals(setOf("a", "b"), cycles.first().path.toSet())
    }

    @Test
    fun `detects a 3-module cycle with the exact path`() {
        val graph = graphOf(
            listOf("a", "b", "c"),
            listOf("a" to "b", "b" to "c", "c" to "a"),
        )

        val cycles = CycleDetector.findCycles(graph)
        assertEquals(1, cycles.size)
        assertEquals(setOf("a", "b", "c"), cycles.first().path.toSet())
        // The path must actually be walkable in order back to its own start.
        val path = cycles.first().path
        for (i in path.indices) {
            val from = path[i]
            val to = path[(i + 1) % path.size]
            assertTrue("expected edge $from -> $to", graph.edges.any { it.from == from && it.to == to })
        }
    }

    @Test
    fun `does not report the same cycle twice regardless of DFS start order`() {
        val graph = graphOf(
            listOf("x", "a", "b", "c"),
            listOf("x" to "a", "a" to "b", "b" to "c", "c" to "a"),
        )

        val cycles = CycleDetector.findCycles(graph)
        assertEquals(1, cycles.size)
    }

    @Test
    fun `an empty graph has no cycles`() {
        assertEquals(emptyList<Cycle>(), CycleDetector.findCycles(ModuleGraph.EMPTY))
    }

    @Test
    fun `two independent cycles are both detected`() {
        val graph = graphOf(
            listOf("a", "b", "c", "d"),
            listOf("a" to "b", "b" to "a", "c" to "d", "d" to "c"),
        )

        val cycles = CycleDetector.findCycles(graph)
        assertEquals(2, cycles.size)
    }
}
