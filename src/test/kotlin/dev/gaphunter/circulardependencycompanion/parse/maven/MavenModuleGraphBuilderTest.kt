package dev.gaphunter.circulardependencycompanion.parse.maven

import dev.gaphunter.circulardependencycompanion.graph.CycleDetector
import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MavenModuleGraphBuilderTest {

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        projectDir = File.createTempFile("cdc-maven-test", "").also {
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

    private fun parentPom(modules: List<String>) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
            <groupId>com.acme</groupId>
            <artifactId>parent</artifactId>
            <version>1.0.0</version>
            <packaging>pom</packaging>
            <modules>
                ${modules.joinToString("\n                ") { "<module>$it</module>" }}
            </modules>
        </project>
    """.trimIndent()

    private fun modulePom(artifactId: String, dependsOnArtifactIds: List<String> = emptyList()) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
            <parent>
                <groupId>com.acme</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
            </parent>
            <artifactId>$artifactId</artifactId>
            <dependencies>
                ${dependsOnArtifactIds.joinToString("\n                ") {
                    "<dependency><groupId>com.acme</groupId><artifactId>$it</artifactId><version>1.0.0</version></dependency>"
                }}
            </dependencies>
        </project>
    """.trimIndent()

    @Test
    fun `multi-module Maven project with no cycles builds a correct graph`() {
        write("pom.xml", parentPom(listOf("web", "core")))
        write("web/pom.xml", modulePom("web", listOf("core")))
        write("core/pom.xml", modulePom("core"))

        val graph = MavenModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.MAVEN, graph.buildSystem)
        assertEquals(setOf("web", "core"), graph.nodes.map { it.name }.toSet())
        assertEquals(1, graph.edges.size)
        assertEquals("web", graph.edges.first().from)
        assertEquals("core", graph.edges.first().to)
        assertTrue(CycleDetector.findCycles(graph).isEmpty())
    }

    @Test
    fun `multi-module Maven project with a cycle is detected`() {
        write("pom.xml", parentPom(listOf("a", "b")))
        write("a/pom.xml", modulePom("a", listOf("b")))
        write("b/pom.xml", modulePom("b", listOf("a")))

        val graph = MavenModuleGraphBuilder.build(projectDir)
        val cycles = CycleDetector.findCycles(graph)

        assertEquals(BuildSystem.MAVEN, graph.buildSystem)
        assertEquals(1, cycles.size)
        assertEquals(setOf("a", "b"), cycles.first().path.toSet())
    }

    @Test
    fun `external dependencies (non-matching groupId-artifactId) never become graph edges`() {
        write("pom.xml", parentPom(listOf("web")))
        write(
            "web/pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <parent><groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version></parent>
                <artifactId>web</artifactId>
                <dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>6.1.0</version></dependency>
                </dependencies>
            </project>
            """.trimIndent(),
        )

        val graph = MavenModuleGraphBuilder.build(projectDir)

        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `single-module Maven project (no modules) returns an empty graph, not a crash`() {
        write(
            "pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.acme</groupId>
                <artifactId>standalone</artifactId>
                <version>1.0.0</version>
            </project>
            """.trimIndent(),
        )

        val graph = MavenModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.NONE, graph.buildSystem)
        assertTrue(graph.nodes.isEmpty())
    }

    @Test
    fun `no pom xml at all returns an empty graph`() {
        val graph = MavenModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.NONE, graph.buildSystem)
    }

    @Test
    fun `a malformed module pom is skipped, never crashes the whole build`() {
        write("pom.xml", parentPom(listOf("web", "broken")))
        write("web/pom.xml", modulePom("web"))
        write("broken/pom.xml", "<project><modules><module>unterminated</project>")

        val graph = MavenModuleGraphBuilder.build(projectDir)

        assertEquals(BuildSystem.MAVEN, graph.buildSystem)
        assertEquals(setOf("web"), graph.nodes.map { it.name }.toSet())
    }

    @Test
    fun `nested modules (a module that itself declares submodules) are all discovered`() {
        write("pom.xml", parentPom(listOf("libs")))
        write(
            "libs/pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <parent><groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version></parent>
                <artifactId>libs</artifactId>
                <packaging>pom</packaging>
                <modules><module>core</module></modules>
            </project>
            """.trimIndent(),
        )
        write("libs/core/pom.xml", modulePom("core"))

        val graph = MavenModuleGraphBuilder.build(projectDir)

        assertEquals(setOf("libs", "core"), graph.nodes.map { it.name }.toSet())
    }
}
