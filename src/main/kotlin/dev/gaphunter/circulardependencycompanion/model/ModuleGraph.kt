package dev.gaphunter.circulardependencycompanion.model

/**
 * The build system detected for the currently open project -- drives
 * which parser ([dev.gaphunter.circulardependencycompanion.parse.gradle.GradleModuleGraphBuilder]
 * or [dev.gaphunter.circulardependencycompanion.parse.maven.MavenModuleGraphBuilder]) runs.
 * A project with neither present is [NONE] -- an honest "nothing to
 * analyze" state, not an error.
 */
enum class BuildSystem {
    GRADLE,
    MAVEN,
    NONE,
}

/**
 * One build-system module (a Gradle `:module` path, or a Maven POM's
 * `artifactId`/`groupId:artifactId`). [name] is what the tool window
 * displays; it is also the graph's node identity, so both parsers are
 * responsible for producing a consistent, unique name per real module
 * before building a [ModuleGraph].
 */
data class ModuleNode(val name: String)

/**
 * A directed dependency edge: [from] declares a build dependency on
 * [to] (`project(":to")` in Gradle, or an inter-module `<dependency>`
 * in Maven). Direction matters for cycle reporting -- a cycle is a
 * path that can be walked forward along these edges back to its own
 * start.
 */
data class ModuleEdge(val from: String, val to: String)

/**
 * The full directed graph of module dependencies for one open project,
 * plus which [BuildSystem] produced it. A single-module project (no
 * submodules declared at all) is represented as [BuildSystem.NONE]
 * with empty [nodes]/[edges] -- the tool window is responsible for
 * telling that apart from "multi-module project with zero
 * dependencies between modules", which is a real, valid, cycle-free
 * graph with nodes but no edges.
 */
data class ModuleGraph(
    val buildSystem: BuildSystem,
    val nodes: List<ModuleNode>,
    val edges: List<ModuleEdge>,
) {
    companion object {
        val EMPTY = ModuleGraph(BuildSystem.NONE, emptyList(), emptyList())
    }
}
