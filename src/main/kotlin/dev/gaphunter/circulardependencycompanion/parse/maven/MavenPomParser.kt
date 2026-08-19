package dev.gaphunter.circulardependencycompanion.parse.maven

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** One `<module>` reference from a parent POM's `<modules>` block, as the raw relative path text (e.g. `core`, `../libs/core`). */
data class PomModuleRef(val relativePath: String)

/**
 * One parsed `pom.xml`: its own coordinates plus every `<dependency>`
 * declared directly under `<dependencies>` (build-reactor-relevant
 * dependencies only -- `<dependencyManagement>` entries are BOM/version
 * pins, not real compile/runtime edges, and are deliberately excluded
 * so the graph never shows a phantom edge for a dependency a module
 * merely constrains the version of but doesn't actually use).
 */
data class ParsedPom(
    val groupId: String?,
    val artifactId: String?,
    val parentGroupId: String?,
    val modules: List<PomModuleRef>,
    val dependencies: List<PomDependencyRef>,
)

data class PomDependencyRef(val groupId: String?, val artifactId: String?)

/**
 * Parses a `pom.xml` with the JDK's own `javax.xml.parsers` DOM API --
 * real, well-formed XML, so unlike Gradle's Groovy/Kotlin DSL (hand-
 * rolled regex parser, see `GradleBuildFileParser`) this needs no
 * custom lexer, per the task brief's own guidance. No bundled Maven/
 * XML IDE plugin dependency -- the JDK's parser is always present.
 */
object MavenPomParser {

    fun parse(pomFile: File): ParsedPom? {
        val document = parseXmlSafely(pomFile) ?: return null
        val root = document.documentElement ?: return null
        if (root.tagName != "project") return null

        val parentElement = firstChildElement(root, "parent")
        val parentGroupId = parentElement?.let { textOfFirstChild(it, "groupId") }

        // A module's own <groupId> is frequently omitted and inherited
        // from <parent><groupId> -- fall back to the parent's, same
        // resolution Maven itself performs, so inter-module dependency
        // matching (MavenModuleGraphBuilder) isn't blind to the common
        // "child POMs omit groupId" convention.
        val ownGroupId = textOfFirstChild(root, "groupId") ?: parentGroupId
        val artifactId = textOfFirstChild(root, "artifactId")

        val modules = firstChildElement(root, "modules")
            ?.let { childElements(it, "module") }
            ?.mapNotNull { it.textContent?.trim()?.takeIf { text -> text.isNotEmpty() } }
            ?.map { PomModuleRef(it) }
            .orEmpty()

        val dependencies = firstChildElement(root, "dependencies")
            ?.let { childElements(it, "dependency") }
            ?.map {
                PomDependencyRef(
                    groupId = textOfFirstChild(it, "groupId"),
                    artifactId = textOfFirstChild(it, "artifactId"),
                )
            }
            .orEmpty()

        return ParsedPom(
            groupId = ownGroupId,
            artifactId = artifactId,
            parentGroupId = parentGroupId,
            modules = modules,
            dependencies = dependencies,
        )
    }

    private fun parseXmlSafely(file: File): Document? = try {
        val factory = DocumentBuilderFactory.newInstance()
        // Never resolve external entities/DTDs while parsing a POM found
        // inside the user's own project -- same "don't trust file content
        // to reach out over the network" discipline as every other
        // static-analysis parser in this catalog. A pom.xml has no
        // legitimate reason to need XXE resolution for this plugin's
        // purposes (module/dependency coordinates only).
        factory.isExpandEntityReferences = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val builder = factory.newDocumentBuilder()
        builder.parse(file)
    } catch (_: Exception) {
        // Malformed XML, unreadable file, or a feature the platform's
        // bundled JDK doesn't support -- always degrade to "no data from
        // this file", never crash the plugin over one bad pom.xml.
        null
    }

    private fun firstChildElement(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) return node
        }
        return null
    }

    private fun childElements(parent: Element, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) result.add(node)
        }
        return result
    }

    private fun textOfFirstChild(parent: Element, tagName: String): String? =
        firstChildElement(parent, tagName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
