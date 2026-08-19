package dev.gaphunter.circulardependencycompanion.parse.gradle

import dev.gaphunter.circulardependencycompanion.graph.CycleDetector
import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import dev.gaphunter.circulardependencycompanion.model.ModuleEdge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GradleModuleGraphBuilderTest {

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        projectDir = File.createTempFile("cdc-gradle-test", "").also {
            it.delete()
            it.mkdirs()
        }
    }

    @After
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    private fun write(relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `multi-module Gradle project with no cycles builds a correct graph`() {
        write("settings.gradle.kts", """include(":app", ":libs:core")""")
        write("app/build.gradle.kts", """dependencies { implementation(project(":libs:core")) }""")
        write("libs/core/build.gradle.kts", "dependencies { }")

        val graph = GradleModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.GRADLE, graph.buildSystem)
        assertEquals(setOf(":app", ":libs:core"), graph.nodes.map { it.name }.toSet())
        assertEquals(listOf(ModuleEdge(":app", ":libs:core")), graph.edges)
        assertTrue(CycleDetector.findCycles(graph).isEmpty())
    }

    @Test
    fun `Gradle project with a 2-module cycle is detected`() {
        write("settings.gradle.kts", """include(":a", ":b")""")
        write("a/build.gradle.kts", """dependencies { implementation(project(":b")) }""")
        write("b/build.gradle.kts", """dependencies { implementation(project(":a")) }""")

        val graph = GradleModuleGraphBuilder.build(projectDir)
        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(setOf(":a", ":b"), cycles.first().path.toSet())
    }

    @Test
    fun `Gradle project with a 3-module cycle is detected with the exact path`() {
        write("settings.gradle.kts", """include(":a", ":b", ":c")""")
        write("a/build.gradle.kts", """dependencies { implementation(project(":b")) }""")
        write("b/build.gradle.kts", """dependencies { implementation(project(":c")) }""")
        write("c/build.gradle.kts", """dependencies { implementation(project(":a")) }""")

        val graph = GradleModuleGraphBuilder.build(projectDir)
        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(setOf(":a", ":b", ":c"), cycles.first().path.toSet())
    }

    @Test
    fun `Groovy DSL settings and build files are parsed the same as Kotlin DSL`() {
        write("settings.gradle", "include ':app', ':libs:core'")
        write("app/build.gradle", "dependencies {\n    implementation project(':libs:core')\n}")
        write("libs/core/build.gradle", "dependencies {}")

        val graph = GradleModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.GRADLE, graph.buildSystem)
        assertEquals(setOf(":app", ":libs:core"), graph.nodes.map { it.name }.toSet())
        assertEquals(listOf(ModuleEdge(":app", ":libs:core")), graph.edges)
    }

    @Test
    fun `single-module project with no settings file returns an empty graph, not a crash`() {
        write("build.gradle.kts", "plugins { id(\"java\") }")

        val graph = GradleModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.NONE, graph.buildSystem)
        assertTrue(graph.nodes.isEmpty())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `settings file with no include calls returns an empty graph`() {
        write("settings.gradle.kts", """rootProject.name = "single"""")

        val graph = GradleModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.NONE, graph.buildSystem)
    }

    @Test
    fun `a malformed module build file is skipped, never crashes the whole build`() {
        write("settings.gradle.kts", """include(":app", ":broken")""")
        write("app/build.gradle.kts", "dependencies { implementation(project(\":broken\")) }")
        write("broken/build.gradle.kts", "dependencies {{{ this is not valid groovy or kotlin at all (((")

        val graph = GradleModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.GRADLE, graph.buildSystem)
        assertEquals(setOf(":app", ":broken"), graph.nodes.map { it.name }.toSet())
        // The malformed file simply yields no extra dependency edges from
        // ":broken" -- it must not throw and must not corrupt ":app"'s
        // already-parsed edge.
        assertEquals(listOf(ModuleEdge(":app", ":broken")), graph.edges)
    }

    @Test
    fun `a project reference to a module not declared in settings is ignored`() {
        write("settings.gradle.kts", """include(":app")""")
        write("app/build.gradle.kts", "dependencies { implementation(project(\":not-included\")) }")

        val graph = GradleModuleGraphBuilder.build(projectDir)

        assertTrue(graph.edges.isEmpty())
    }
}
