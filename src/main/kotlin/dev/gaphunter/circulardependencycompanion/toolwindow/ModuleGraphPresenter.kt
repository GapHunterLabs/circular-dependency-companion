package dev.gaphunter.circulardependencycompanion.toolwindow

import dev.gaphunter.circulardependencycompanion.graph.Cycle
import dev.gaphunter.circulardependencycompanion.model.ModuleGraph

/**
 * Turns a raw [ModuleGraph] into a **layered** view -- modules grouped
 * by topological depth (layer 0 = modules with no dependencies among
 * this graph's own nodes, layer 1 = modules that only depend on layer
 * 0, and so on) -- for [CircularDependencyToolWindow] to render as an
 * indented list. Pure data transformation, no Swing here, so it's
 * covered directly by [dev.gaphunter.circulardependencycompanion.graph.CycleDetectorTest]-style
 * unit tests without needing `BasePlatformTestCase`/EDT.
 *
 * **Why layers-by-depth instead of a real node-and-edge diagram** (the
 * design decision the task brief asked to make and justify, same as
 * "inlay vs gutter icon" in earlier plugins in this catalog): a true
 * graphical renderer (`Graphics2D` with hand-computed node positions
 * and drawn edge lines, or an embedded graph-layout library) is a
 * meaningfully larger and riskier scope than this catalog's proven
 * `Tree`/`DefaultMutableTreeNode` tool-window pattern (already shipped
 * in `xsd-companion`/`gitlab-ci-companion`, both verified 6/6
 * Compatible) -- edge-routing/overlap-avoidance for an arbitrary
 * module graph is a real layout algorithm problem on its own, not a
 * few hours of polish. A layered list keeps every requirement the task
 * brief actually asks for: nodes and edges are both fully visible (an
 * edge is "this module, indented one level deeper, appears under its
 * dependency's layer" plus an explicit `-> target` suffix so the
 * relationship reads left-to-right without needing a drawn line), and
 * -- the part that matters most -- **cycles are impossible to miss**,
 * called out in their own top-level section with the exact path,
 * before the reader even has to interpret the layered list. A future
 * version could add a real graphical layout without changing anything
 * below this class -- [LayeredGraphView] is renderer-agnostic on
 * purpose.
 */
object ModuleGraphPresenter {

    data class LayeredGraphView(
        val layers: List<List<ModuleLayerEntry>>,
        val cycles: List<Cycle>,
        /** Modules that belong to at least one cycle -- both the tree render and any future highlighting use this to mark a node, without re-deriving it from [cycles] at render time. */
        val moduleNamesInCycles: Set<String>,
    )

    data class ModuleLayerEntry(val name: String, val dependsOn: List<String>)

    fun buildLayeredView(graph: ModuleGraph, cycles: List<Cycle>): LayeredGraphView {
        val dependsOnByModule: Map<String, List<String>> = graph.edges
            .groupBy({ it.from }, { it.to })
        val moduleNamesInCycles = cycles.flatMap { it.path }.toSet()

        // Depth = 1 + max(depth of everything this module depends on).
        // Modules on a cycle can't get a well-defined depth this way (the
        // recursion never bottoms out) -- they're assigned depth 0 and
        // rendered in layer 0 regardless of their real dependencies, since
        // "which layer" is a secondary concern once a module is already
        // flagged as part of a cycle in its own dedicated section.
        val depthCache = HashMap<String, Int>()
        val inProgress = mutableSetOf<String>()

        fun depthOf(module: String): Int {
            depthCache[module]?.let { return it }
            if (module in moduleNamesInCycles) {
                depthCache[module] = 0
                return 0
            }
            if (!inProgress.add(module)) {
                // Defensive fallback: a cycle CycleDetector somehow didn't
                // flag (shouldn't happen, but never hang/stack-overflow a
                // UI-facing computation over a graph edge case).
                return 0
            }
            val deps = dependsOnByModule[module].orEmpty()
            val depth = if (deps.isEmpty()) 0 else (deps.maxOf { depthOf(it) } + 1)
            inProgress.remove(module)
            depthCache[module] = depth
            return depth
        }

        val entriesByDepth = sortedMapOf<Int, MutableList<ModuleLayerEntry>>()
        for (node in graph.nodes) {
            val depth = depthOf(node.name)
            entriesByDepth.getOrPut(depth) { mutableListOf() }
                .add(ModuleLayerEntry(node.name, dependsOnByModule[node.name].orEmpty()))
        }
        for (list in entriesByDepth.values) list.sortBy { it.name }

        return LayeredGraphView(
            layers = entriesByDepth.values.toList(),
            cycles = cycles,
            moduleNamesInCycles = moduleNamesInCycles,
        )
    }
}
