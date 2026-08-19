package dev.gaphunter.circulardependencycompanion.parse.gradle

import org.junit.Assert.assertEquals
import org.junit.Test

class GradleBuildFileParserTest {

    @Test
    fun `parses Groovy DSL project dependency`() {
        val text = """
            dependencies {
                implementation project(':libs:core')
                testImplementation project(':libs:test-utils')
            }
        """.trimIndent()

        assertEquals(
            listOf(":libs:core", ":libs:test-utils"),
            GradleBuildFileParser.parseProjectDependencies(text),
        )
    }

    @Test
    fun `parses Kotlin DSL project dependency with double quotes`() {
        val text = """
            dependencies {
                implementation(project(":libs:core"))
                api(project(":libs:api-contracts"))
            }
        """.trimIndent()

        assertEquals(
            listOf(":libs:core", ":libs:api-contracts"),
            GradleBuildFileParser.parseProjectDependencies(text),
        )
    }

    @Test
    fun `parses Kotlin DSL project dependency with an explicit configuration argument`() {
        val text = """dependencies { implementation(project(":libs:core", configuration = "default")) }"""

        assertEquals(listOf(":libs:core"), GradleBuildFileParser.parseProjectDependencies(text))
    }

    @Test
    fun `ignores external library dependencies`() {
        val text = """
            dependencies {
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation(project(":libs:core"))
            }
        """.trimIndent()

        assertEquals(listOf(":libs:core"), GradleBuildFileParser.parseProjectDependencies(text))
    }

    @Test
    fun `returns empty list for a build file with no project dependencies`() {
        val text = "plugins { id(\"java\") }"

        assertEquals(emptyList<String>(), GradleBuildFileParser.parseProjectDependencies(text))
    }

    @Test
    fun `does not crash on malformed build file text`() {
        val text = "dependencies {{{ implementation(project(::: not valid"

        GradleBuildFileParser.parseProjectDependencies(text)
    }
}
