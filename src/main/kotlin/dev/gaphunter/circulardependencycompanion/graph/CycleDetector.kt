package dev.gaphunter.circulardependencycompanion.graph

import dev.gaphunter.circulardependencycompanion.model.ModuleGraph

/**
 * One detected cycle, as the ordered list of module names walked to
 * find it, e.g. `["a", "b", "c"]` for the cycle `a -> b -> c -> a` --
 * the closing edge back to `a` is implied, never repeated as a
 * trailing element, so callers format the arrow chain themselves
 * (`path.joinToString(" -> ") + " -> " + path.first()`).
 */
data class Cycle(val path: List<String>)

/**
 * Standard directed-graph cycle detection: DFS with three-color
 * marking (white/gray/black, tracked here as `UNVISITED`/`IN_STACK`/
 * `DONE`) -- a back-edge to a node still `IN_STACK` (i.e. an ancestor
 * on the current DFS path, not just any previously-seen node) is a
 * real cycle. Plain "have I seen this node before" (two-color) would
 * wrongly flag a diamond dependency (`a -> b -> d`, `a -> c -> d`) as
 * a cycle even though `d` is never revisited while still on the
 * stack -- three-color is the textbook fix and the only correct
 * choice here.
 */
object CycleDetector {

    private enum class State { UNVISITED, IN_STACK, DONE }

    fun findCycles(graph: ModuleGraph): List<Cycle> {
        val adjacency: Map<String, List<String>> = graph.edges
            .groupBy({ it.from }, { it.to })
        val state = HashMap<String, State>()
        for (node in graph.nodes) state[node.name] = State.UNVISITED

        val cycles = mutableListOf<Cycle>()
        // Dedup by the *set* of nodes involved plus its rotation-normalized
        // path, so the same real cycle reached via two different starting
        // nodes in the outer loop below isn't reported twice.
        val seenCycleKeys = mutableSetOf<String>()

        val stack = ArrayDeque<String>()

        fun dfs(node: String) {
            state[node] = State.IN_STACK
            stack.addLast(node)

            for (next in adjacency[node].orEmpty()) {
                when (state[next]) {
                    State.IN_STACK -> {
                        // Found a back-edge to an ancestor still on the
                        // stack -- extract just the cycle portion (from
                        // that ancestor's position to the top), not the
                        // whole DFS path that led there.
                        val startIdx = stack.indexOf(next)
                        val cyclePath = stack.toList().subList(startIdx, stack.size)
                        val key = normalizedCycleKey(cyclePath)
                        if (seenCycleKeys.add(key)) {
                            cycles.add(Cycle(cyclePath))
                        }
                    }
                    State.DONE -> {
                        // Already fully explored via another path -- not a
                        // cycle relative to the current stack, nothing to do.
                    }
                    State.UNVISITED, null -> dfs(next)
                }
            }

            stack.removeLast()
            state[node] = State.DONE
        }

        for (node in graph.nodes) {
            if (state[node.name] == State.UNVISITED) dfs(node.name)
        }

        return cycles
    }

    /**
     * Rotates [path] so it starts at its lexicographically smallest
     * element, giving the same key for the same cycle regardless of
     * which node the outer DFS loop happened to reach first (e.g.
     * `[a, b, c]` and `[b, c, a]` describe the identical cycle).
     */
    private fun normalizedCycleKey(path: List<String>): String {
        if (path.isEmpty()) return ""
        val minIdx = path.indices.minBy { path[it] }
        val rotated = path.subList(minIdx, path.size) + path.subList(0, minIdx)
        return rotated.joinToString("->")
    }
}
