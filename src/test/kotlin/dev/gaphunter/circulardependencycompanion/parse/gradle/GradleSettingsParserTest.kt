package dev.gaphunter.circulardependencycompanion.parse.gradle

import org.junit.Assert.assertEquals
import org.junit.Test

class GradleSettingsParserTest {

    @Test
    fun `parses Groovy DSL single-quoted includes, one per line`() {
        val text = """
            rootProject.name = 'demo'
            include ':app'
            include ':libs:core'
        """.trimIndent()

        assertEquals(listOf(":app", ":libs:core"), GradleSettingsParser.parseIncludedModules(text))
    }

    @Test
    fun `parses Groovy DSL comma-separated includes on one line`() {
        val text = "include 'app', ':libs:core', \"libs:util\""

        assertEquals(listOf("app", ":libs:core", "libs:util"), GradleSettingsParser.parseIncludedModules(text))
    }

    @Test
    fun `parses Kotlin DSL parenthesized includes`() {
        val text = """
            rootProject.name = "demo"
            include(":app")
            include(":libs:core", ":libs:util")
        """.trimIndent()

        assertEquals(
            listOf(":app", ":libs:core", ":libs:util"),
            GradleSettingsParser.parseIncludedModules(text),
        )
    }

    @Test
    fun `returns empty list for a settings file with no include calls`() {
        val text = "rootProject.name = \"single-module-demo\""

        assertEquals(emptyList<String>(), GradleSettingsParser.parseIncludedModules(text))
    }

    @Test
    fun `does not crash on malformed settings text`() {
        val text = "include(:::: not valid kotlin at all {{{"

        // Just must not throw -- zero or partial matches are both acceptable,
        // a crash is not.
        GradleSettingsParser.parseIncludedModules(text)
    }
}
