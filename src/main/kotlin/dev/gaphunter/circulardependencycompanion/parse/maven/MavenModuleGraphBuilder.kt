package dev.gaphunter.circulardependencycompanion.parse.maven

import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import dev.gaphunter.circulardependencycompanion.model.ModuleEdge
import dev.gaphunter.circulardependencycompanion.model.ModuleGraph
import dev.gaphunter.circulardependencycompanion.model.ModuleNode
import java.io.File

/**
 * Builds a [ModuleGraph] for a Maven multi-module project rooted at
 * [projectDir]: parses the root `pom.xml`'s `<modules>` list
 * ([MavenPomParser]), recursively follows each `<module>` relative
 * path to discover the *entire* module tree (a multi-module Maven
 * project can nest -- a module's own `pom.xml` can declare further
 * `<modules>` of its own), then, for every discovered module, marks a
 * dependency edge to another discovered module when a
 * `<dependency>`'s `groupId:artifactId` matches that other module's
 * own coordinates -- the exact signal the task brief specifies:
 * "`groupId` que coincide con el `groupId` del propio proyecto
 * multi-módulo" as evidence of an inter-module dependency rather than
 * an external library.
 *
 * A root `pom.xml` with no `<modules>` at all is a **single-module
 * project** -- returns [ModuleGraph.EMPTY] with [BuildSystem.NONE],
 * same honest treatment as [dev.gaphunter.circulardependencycompanion.parse.gradle.GradleModuleGraphBuilder]
 * for a Gradle project without `include(...)`.
 */
object MavenModuleGraphBuilder {

    private data class DiscoveredModule(
        val name: String,
        val groupId: String?,
        val artifactId: String?,
        val dependencies: List<PomDependencyRef>,
    )

    fun build(projectDir: File): ModuleGraph {
        val rootPomFile = File(projectDir, "pom.xml")
        if (!rootPomFile.isFile) return ModuleGraph.EMPTY

        val rootPom = MavenPomParser.parse(rootPomFile) ?: return ModuleGraph.EMPTY
        if (rootPom.modules.isEmpty()) return ModuleGraph.EMPTY

        val discovered = mutableListOf<DiscoveredModule>()
        val visitedDirs = mutableSetOf<String>()
        collectModules(projectDir, rootPom, discovered, visitedDirs)

        if (discovered.isEmpty()) return ModuleGraph.EMPTY

        val nodes = discovered.map { ModuleNode(it.name) }
        // groupId:artifactId -> module name, so a <dependency> can be
        // resolved to "is this actually one of our own modules" without
        // an O(n^2) scan per dependency.
        val byCoordinate: Map<String, String> = discovered
            .filter { it.groupId != null && it.artifactId != null }
            .associate { "${it.groupId}:${it.artifactId}" to it.name }

        val edges = mutableListOf<ModuleEdge>()
        for (module in discovered) {
            for (dep in module.dependencies) {
                if (dep.groupId == null || dep.artifactId == null) continue
                val targetName = byCoordinate["${dep.groupId}:${dep.artifactId}"] ?: continue
                if (targetName == module.name) continue // a module never depends on itself
                edges.add(ModuleEdge(from = module.name, to = targetName))
            }
        }

        return ModuleGraph(BuildSystem.MAVEN, nodes, edges.distinct())
    }

    /**
     * Recursively walks `<modules>` starting at [pom] (already parsed)
     * located in [dir], appending every module found to [into]. Guards
     * against re-visiting the same directory twice ([visitedDirs],
     * normalized via [File.getCanonicalPath]) -- a malformed POM tree
     * with a `<module>` cycle in the *filesystem layout itself* (not to
     * be confused with a dependency cycle, which is exactly what this
     * plugin is built to detect and display, not silently avoid) would
     * otherwise recurse forever.
     */
    private fun collectModules(
        dir: File,
        pom: ParsedPom,
        into: MutableList<DiscoveredModule>,
        visitedDirs: MutableSet<String>,
    ) {
        val canonicalDir = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
        if (!visitedDirs.add(canonicalDir)) return

        for (moduleRef in pom.modules) {
            val moduleDir = File(dir, moduleRef.relativePath)
            val modulePomFile = File(moduleDir, "pom.xml")
            val modulePom = MavenPomParser.parse(modulePomFile) ?: continue
            val moduleName = moduleNameFor(modulePom, moduleRef.relativePath)

            into.add(
                DiscoveredModule(
                    name = moduleName,
                    groupId = modulePom.groupId,
                    artifactId = modulePom.artifactId,
                    dependencies = modulePom.dependencies,
                ),
            )

            if (modulePom.modules.isNotEmpty()) {
                collectModules(moduleDir, modulePom, into, visitedDirs)
            }
        }
    }

    /** Prefers the real `artifactId`; falls back to the relative path if a module's POM is somehow missing one. */
    private fun moduleNameFor(pom: ParsedPom, relativePath: String): String =
        pom.artifactId ?: relativePath
}
