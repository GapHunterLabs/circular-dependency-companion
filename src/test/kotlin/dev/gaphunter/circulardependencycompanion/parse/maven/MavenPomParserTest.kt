package dev.gaphunter.circulardependencycompanion.parse.maven

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class MavenPomParserTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = File.createTempFile("cdc-pom-test", "").also {
            it.delete()
            it.mkdirs()
        }
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun writePom(dir: File, xml: String): File {
        dir.mkdirs()
        val file = File(dir, "pom.xml")
        file.writeText(xml)
        return file
    }

    @Test
    fun `parses modules and dependencies from a parent POM`() {
        val pom = writePom(
            tmp,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.acme</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
                <packaging>pom</packaging>
                <modules>
                    <module>core</module>
                    <module>web</module>
                </modules>
            </project>
            """.trimIndent(),
        )

        val parsed = MavenPomParser.parse(pom)
        assertNotNull(parsed)
        assertEquals("com.acme", parsed!!.groupId)
        assertEquals(listOf(PomModuleRef("core"), PomModuleRef("web")), parsed.modules)
    }

    @Test
    fun `parses dependencies with explicit groupId`() {
        val pom = writePom(
            tmp,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.acme</groupId>
                <artifactId>web</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>com.acme</groupId>
                        <artifactId>core</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-core</artifactId>
                        <version>6.1.0</version>
                    </dependency>
                </dependencies>
            </project>
            """.trimIndent(),
        )

        val parsed = MavenPomParser.parse(pom)
        assertNotNull(parsed)
        assertEquals(
            listOf(
                PomDependencyRef("com.acme", "core"),
                PomDependencyRef("org.springframework", "spring-core"),
            ),
            parsed!!.dependencies,
        )
    }

    @Test
    fun `inherits groupId from parent when own groupId is omitted`() {
        val pom = writePom(
            tmp,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <parent>
                    <groupId>com.acme</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                <artifactId>core</artifactId>
            </project>
            """.trimIndent(),
        )

        val parsed = MavenPomParser.parse(pom)
        assertNotNull(parsed)
        assertEquals("com.acme", parsed!!.groupId)
        assertEquals("core", parsed.artifactId)
    }

    @Test
    fun `returns null for malformed XML instead of throwing`() {
        val pom = writePom(tmp, "<project><modules><module>core</modules></project>")

        assertNull(MavenPomParser.parse(pom))
    }

    @Test
    fun `returns null for a nonexistent file instead of throwing`() {
        assertNull(MavenPomParser.parse(File(tmp, "does-not-exist.xml")))
    }
}
