package dev.gaphunter.circulardependencycompanion.parse.gradle

/**
 * Extracts declared module paths (`:module`, `:group:module`, ...) from
 * the text of a `settings.gradle`/`settings.gradle.kts` file. Same
 * "hand-rolled scanner over a small, stable, line-oriented syntax"
 * pattern as `DockerfileParser`/`NginxLexer` (`CONSTITUTION.md` §6) --
 * this is 100% static text analysis, never a real Gradle
 * evaluation/daemon.
 *
 * Covers both DSL flavors with the same two regexes, because the
 * syntax difference between them is minor for this one statement:
 * - Groovy DSL: `include 'a', ':b', "c"` (no parens required; commas
 *   separate multiple modules in a single call; quotes single or double).
 * - Kotlin DSL: `include(":a")`, `include(":a", ":b")` (parens
 *   required; quotes always double).
 */
object GradleSettingsParser {

    // Matches the argument list of an `include(...)`/`include ...` call,
    // capturing everything between the first quote after "include" and
    // the end of that statement's quoted-argument list. Deliberately
    // simple: real settings files essentially always write one `include`
    // call per line or a short comma-separated list, never a
    // programmatically generated list (that would need real Gradle
    // evaluation to resolve, out of scope for static analysis, same
    // honest limitation as ARG-based Dockerfile paths).
    private val INCLUDE_LINE = Regex("""\binclude\s*\(?\s*((?:['"][^'"]+['"]\s*,?\s*)+)\)?""")
    private val QUOTED_MODULE = Regex("""['"]([^'"]+)['"]""")

    /**
     * Returns each declared module path exactly as written (e.g.
     * `:app`, `:libs:core`) -- callers normalize separators/leading
     * colons when turning these into [dev.gaphunter.circulardependencycompanion.model.ModuleNode]
     * names, this function only extracts the raw literals.
     */
    fun parseIncludedModules(text: String): List<String> {
        val modules = mutableListOf<String>()
        for (lineMatch in INCLUDE_LINE.findAll(text)) {
            val argsText = lineMatch.groupValues[1]
            for (quoted in QUOTED_MODULE.findAll(argsText)) {
                modules.add(quoted.groupValues[1])
            }
        }
        return modules
    }
}
