package dev.gaphunter.circulardependencycompanion.graph

import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import dev.gaphunter.circulardependencycompanion.model.ModuleGraph
import dev.gaphunter.circulardependencycompanion.parse.gradle.GradleModuleGraphBuilder
import dev.gaphunter.circulardependencycompanion.parse.maven.MavenModuleGraphBuilder
import java.io.File

/** The result of analyzing one project root directory: the graph found, plus the cycles detected in it. */
data class ProjectAnalysisResult(val graph: ModuleGraph, val cycles: List<Cycle>) {
    companion object {
        val EMPTY = ProjectAnalysisResult(ModuleGraph.EMPTY, emptyList())
    }
}

/**
 * Top-level entry point tying the two build-system-specific graph
 * builders together with [CycleDetector]. Gradle is tried first --
 * **deliberate priority, not arbitrary**: a project can technically
 * have both a stray `pom.xml` (e.g. left over from a partial
 * migration, or a submodule embedding an unrelated Maven-based tool)
 * and a real `settings.gradle(.kts)` at the same root; Gradle's own
 * presence of `settings.gradle(.kts)` is the stronger, more specific
 * signal of "this is the real build system for the project as a
 * whole" than merely finding *a* `pom.xml` file. If Gradle produces no
 * graph (either file absent, or present but genuinely single-module),
 * Maven is tried as the fallback.
 */
object ProjectGraphAnalyzer {

    fun analyze(projectDir: File): ProjectAnalysisResult {
        val gradleGraph = GradleModuleGraphBuilder.build(projectDir)
        val graph = if (gradleGraph.buildSystem == BuildSystem.GRADLE) {
            gradleGraph
        } else {
            MavenModuleGraphBuilder.build(projectDir)
        }

        if (graph.buildSystem == BuildSystem.NONE) return ProjectAnalysisResult.EMPTY

        val cycles = CycleDetector.findCycles(graph)
        return ProjectAnalysisResult(graph, cycles)
    }
}
