package dev.gaphunter.circulardependencycompanion.parse.gradle

import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import dev.gaphunter.circulardependencycompanion.model.ModuleEdge
import dev.gaphunter.circulardependencycompanion.model.ModuleGraph
import dev.gaphunter.circulardependencycompanion.model.ModuleNode
import java.io.File

/**
 * Builds a [ModuleGraph] for a Gradle multi-module project rooted at
 * [projectDir]: finds `settings.gradle(.kts)`, parses its declared
 * `include(...)` modules ([GradleSettingsParser]), then for each
 * module locates and parses its own `build.gradle(.kts)`
 * ([GradleBuildFileParser]) for `project(":...")` references. Pure
 * filesystem + text reads -- no Gradle daemon, no build evaluation,
 * same static-analysis-only principle as every other parser in this
 * catalog (`CONSTITUTION.md` §6).
 *
 * A project with no `settings.gradle(.kts)` at all, or one whose
 * `include(...)` list is empty, is a **single-module project** --
 * returns [ModuleGraph.EMPTY] with [BuildSystem.NONE], never a crash
 * or a misleading empty-but-"multi-module" graph. Distinguishing this
 * honestly from "multi-module with zero inter-module dependencies" is
 * the tool window's job (see `CircularDependencyToolWindow`), not this
 * builder's -- this builder only reports `BuildSystem.GRADLE` when it
 * found a real, non-empty module list.
 */
object GradleModuleGraphBuilder {

    private val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")
    private val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")

    fun build(projectDir: File): ModuleGraph {
        val settingsFile = SETTINGS_FILE_NAMES
            .map { File(projectDir, it) }
            .firstOrNull { it.isFile }
            ?: return ModuleGraph.EMPTY

        val settingsText = readTextSafely(settingsFile) ?: return ModuleGraph.EMPTY
        val rawModulePaths = GradleSettingsParser.parseIncludedModules(settingsText)
        if (rawModulePaths.isEmpty()) return ModuleGraph.EMPTY

        val normalizedNames = rawModulePaths.map { normalizeModuleName(it) }.distinct()
        val nodes = normalizedNames.map { ModuleNode(it) }
        val knownModules = normalizedNames.toSet()

        val edges = mutableListOf<ModuleEdge>()
        for (rawPath in rawModulePaths.distinct()) {
            val moduleName = normalizeModuleName(rawPath)
            val moduleDir = resolveModuleDir(projectDir, rawPath)
            val buildFile = BUILD_FILE_NAMES
                .map { File(moduleDir, it) }
                .firstOrNull { it.isFile }
                ?: continue

            val buildText = readTextSafely(buildFile) ?: continue
            val dependencyPaths = GradleBuildFileParser.parseProjectDependencies(buildText)
            for (depPath in dependencyPaths) {
                val depName = normalizeModuleName(depPath)
                // Only report edges to modules this project actually
                // declares in settings -- a project(":x") referencing a
                // module that isn't included anywhere is a malformed/
                // stale reference, not a real graph edge to visualize.
                if (depName in knownModules) {
                    edges.add(ModuleEdge(from = moduleName, to = depName))
                }
            }
        }

        return ModuleGraph(BuildSystem.GRADLE, nodes, edges.distinct())
    }

    /** `:libs:core` and `libs:core` both normalize to `:libs:core`. */
    private fun normalizeModuleName(rawPath: String): String {
        val trimmed = rawPath.trim()
        return if (trimmed.startsWith(":")) trimmed else ":$trimmed"
    }

    /** `:libs:core` -> `<projectDir>/libs/core`, Gradle's own convention. */
    private fun resolveModuleDir(projectDir: File, rawPath: String): File {
        val segments = rawPath.trim().removePrefix(":").split(":").filter { it.isNotEmpty() }
        var dir = projectDir
        for (segment in segments) dir = File(dir, segment)
        return dir
    }

    private fun readTextSafely(file: File): String? = try {
        file.readText()
    } catch (_: Exception) {
        // Unreadable (permissions, mid-write, symlink loop, etc.) -- treat
        // as "no data from this file", never propagate a crash for what
        // is, from the plugin's point of view, just a source of text.
        null
    }
}
