package dev.gaphunter.circulardependencycompanion.parse.gradle

/**
 * Extracts inter-module `project(...)` dependency references from the
 * text of one module's `build.gradle`/`build.gradle.kts`. Static text/
 * regex analysis only -- no real Gradle evaluation, same principle as
 * [GradleSettingsParser].
 *
 * **Configurations covered (deliberately the common ones, not literally
 * every configuration Gradle/AGP/KMP ever define -- same "cover the
 * common subset, document what's not" honesty as
 * `DockerignoreMatcher`):** `implementation`, `api`, `compileOnly`,
 * `runtimeOnly`, `testImplementation`, `testApi`, `testRuntimeOnly`,
 * `annotationProcessor`, `kapt`, `ksp`, `feature` -- each optionally
 * prefixed as they commonly are in real multi-module builds. Any
 * *other* configuration name is still matched, because the pattern
 * this looks for is really just `<identifier>(project(...))` /
 * `<identifier> project(...)` regardless of which word introduces it --
 * the curated list above exists only for documentation/README honesty
 * about what's been explicitly tested, not as a hard allowlist that
 * silently drops unknown configurations.
 */
object GradleBuildFileParser {

    // Matches `project(":module")`, `project(':module')`, and the
    // Kotlin-DSL-only shorthand `project(":module", configuration = "default")`
    // (the second argument, if present, is irrelevant to module identity
    // and simply not captured). Deliberately does NOT require a specific
    // configuration keyword before `project(...)` -- see class doc for
    // why that's intentional, not an oversight.
    private val PROJECT_DEPENDENCY = Regex("""\bproject\s*\(\s*['"]([^'"]+)['"]""")

    /**
     * Returns every `project(":...")` path this build file references,
     * exactly as written, in source order (duplicates kept -- callers
     * that only need distinct edges de-duplicate downstream, same
     * "extraction is dumb, interpretation is smart" split as the rest
     * of this catalog's parsers).
     */
    fun parseProjectDependencies(text: String): List<String> {
        return PROJECT_DEPENDENCY.findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }
}
