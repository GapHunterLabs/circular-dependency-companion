package dev.gaphunter.circulardependencycompanion.graph

import dev.gaphunter.circulardependencycompanion.model.BuildSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ProjectGraphAnalyzerTest {

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        projectDir = File.createTempFile("cdc-analyzer-test", "").also {
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
    fun `a completely empty directory is treated as single-module, not a crash`() {
        val result = ProjectGraphAnalyzer.analyze(projectDir)

        assertEquals(BuildSystem.NONE, result.graph.buildSystem)
        assertTrue(result.cycles.isEmpty())
    }

    @Test
    fun `Gradle settings file takes priority over a stray pom xml at the same root`() {
        write("settings.gradle.kts", """include(":app")""")
        write("app/build.gradle.kts", "dependencies {}")
        // A stray pom.xml left over from an unrelated tool/migration.
        write(
            "pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project><groupId>x</groupId><artifactId>y</artifactId><version>1.0</version></project>
            """.trimIndent(),
        )

        val result = ProjectGraphAnalyzer.analyze(projectDir)

        assertEquals(BuildSystem.GRADLE, result.graph.buildSystem)
    }

    @Test
    fun `falls back to Maven when no Gradle settings file is present`() {
        write(
            "pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.acme</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
                <modules><module>core</module></modules>
            </project>
            """.trimIndent(),
        )
        write(
            "core/pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <parent><groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version></parent>
                <artifactId>core</artifactId>
            </project>
            """.trimIndent(),
        )

        val result = ProjectGraphAnalyzer.analyze(projectDir)

        assertEquals(BuildSystem.MAVEN, result.graph.buildSystem)
    }

    @Test
    fun `end-to-end cycle detection surfaces through analyze()`() {
        write("settings.gradle.kts", """include(":a", ":b")""")
        write("a/build.gradle.kts", "dependencies { implementation(project(\":b\")) }")
        write("b/build.gradle.kts", "dependencies { implementation(project(\":a\")) }")

        val result = ProjectGraphAnalyzer.analyze(projectDir)

        assertEquals(1, result.cycles.size)
    }
}
