# Circular Dependency Companion

IntelliJ-family plugin. Visualizes the dependency graph between
**modules** of a multi-module Gradle or Maven project, and highlights
real circular dependencies when they exist — module/build-system
level, not file/class-level "circular import" (already covered by
several existing plugins). 100% static text/XML analysis of files
already open in your project: no Gradle daemon, no Maven reactor
build, no network call.

## Why it exists

An original idea, not a port of an existing competitor — validated
against `CONSTITUTION.md` §1's "Plan B permanente" discipline before
being built: (1) confirmed no plugin in this catalog or in JetBrains
Marketplace does exactly this (results for `"circular dependency
java"` are generic architecture visualizers or package analyzers, none
focused specifically on build-module cycles); (2) confirmed buildable
in the ~10-day budget with techniques this catalog already has proven
— a hand-rolled text/regex parser for Gradle DSL (same pattern as
`NginxLexer`/`DockerfileParser`), the JDK's own XML parser for Maven
POMs, a textbook directed-graph cycle detector, and the
`Tree`/`DefaultMutableTreeNode` tool-window pattern already shipped
twice in this catalog (`xsd-companion`, `gitlab-ci-companion`). Same
"apuesta consciente sin ancla de mercado" treatment as Refactor
Simulator / Test Scaffold Companion / Dockerfile Layer Size Companion:
v0.1 ships free, no time/marketing investment disproportionate to real
demand signal until there's evidence of adoption.

## What it does

Open a multi-module Gradle or Maven project and open the **"Module
Dependencies"** tool window (bottom, same anchor as GitLab CI
Companion's tool window). It shows:

- Every module discovered, grouped into **layers by dependency
  depth** — modules with no dependencies on other project modules in
  layer 0, modules that only depend on layer-0 modules in layer 1, and
  so on. Each entry also lists exactly what it depends on
  (`:app  ->  :libs:core, :libs:util`).
- A **"Cycles detected"** section at the top, in red, listing the
  exact path of every real cycle found (`a -> b -> c -> a`) — never
  mixed silently into the regular layer list. If there are no cycles,
  this says so explicitly ("No cycles detected"), not just an absence
  of a section.
- A manual **Refresh** action in the tool window's toolbar — the graph
  is built once when the tool window opens and re-built on demand,
  not on every keystroke (see "Why built this way" below for the
  reasoning).

## Gradle support

Parses `settings.gradle`/`settings.gradle.kts` for declared
`include(...)` modules, and each module's own
`build.gradle`/`build.gradle.kts` for `project(":...")` dependency
references. **Both Groovy and Kotlin DSL are supported** — the two
flavors differ only in minor syntax (parens required or not, quote
style) for the one statement shape this plugin looks for, so both are
matched by the same pair of regexes.

**Configurations covered (a documented common subset, not literally
every configuration Gradle/AGP/KMP ever define):** `implementation`,
`api`, `compileOnly`, `runtimeOnly`, `testImplementation`, `testApi`,
`testRuntimeOnly`, `annotationProcessor`, `kapt`, `ksp`, and others —
in practice the parser doesn't require a specific configuration
keyword before `project(...)` at all (see the extraction code's own
comment for why), so a project(...) reference is caught wherever it's
declared, not just under a curated allowlist of keywords.

**Known limitation, stated honestly:** a project that computes its
module list or `project(...)` targets programmatically (a loop over a
directory listing, a variable built at Gradle-evaluation time) needs a
real Gradle evaluation to resolve — that's out of scope for static
text analysis, same category of limitation as Dockerfile Layer Size
Companion's `ARG`-based path substitution. The overwhelming majority of
real-world multi-module Gradle projects declare `include(...)` and
`project(...)` as literal string arguments, which this plugin handles
correctly.

## Maven support

Parses each `pom.xml`'s `<modules>` list (recursively — a module's own
POM can declare further `<modules>` of its own, and this plugin
follows that nesting) and looks at every `<dependency>` declared
directly under `<dependencies>`. A dependency is treated as an
**inter-module edge** when its `groupId:artifactId` matches another
module discovered in the same project tree — the exact signal the
task brief specified as evidence of "this is one of our own modules,
not an external library." `<dependencyManagement>` entries are
deliberately excluded (they're version pins/BOM declarations, not real
compile/runtime edges — including them would show a phantom edge for a
dependency a module merely constrains the version of without actually
using).

Uses the JDK's own `javax.xml.parsers` (DOM), not a bundled Maven or
XML IDE plugin — real, well-formed XML needs no custom lexer the way
Gradle's Groovy/Kotlin DSL does.

## Cycle detection

Standard directed-graph algorithm: depth-first search with three-color
marking (unvisited / on-the-current-stack / fully explored). A
back-edge to a node still on the current DFS stack is a real cycle;
a plain "have I seen this node anywhere before" check would wrongly
flag a harmless diamond dependency (`a -> b -> d`, `a -> c -> d`) as a
cycle, since `d` is reached twice but never actually revisited while
still on the stack — three-color marking is the textbook fix and the
only correct choice. Cycles are de-duplicated by rotating each found
path to start at its lexicographically smallest module, so the same
real cycle reached from two different starting points in the outer
scan is only reported once.

## Single-module projects: an honest empty state, not a blank panel

A project with no submodules declared (`settings.gradle(.kts)` absent,
or present with no `include(...)` calls; a `pom.xml` with no
`<modules>`) shows **"No cycles possible: single-module project"** in
the tool window — never an empty, unexplained panel that leaves the
user guessing whether the plugin is broken or the project genuinely
has nothing to show.

## Why a layered list, not a hand-drawn graph diagram

Two rendering shapes were considered for the tool window, same design
decision every other Companion in this catalog has had to make and
document:

- **A true graphical renderer** (`Graphics2D` with computed node
  positions and drawn edge lines, or an embedded graph-layout library)
  would show the graph in the most visually familiar shape, but
  edge-routing and overlap-avoidance for an arbitrary module graph is
  a real layout-algorithm problem on its own — meaningfully larger and
  riskier scope than a ~10-day budget comfortably supports, and no
  plugin in this catalog has shipped this kind of renderer yet (no
  proven `verifyPlugin` track record to build on).
- **A layered, indented tree** (`Tree`/`DefaultMutableTreeNode`, the
  exact mechanism `xsd-companion`'s Structure tool window and
  `gitlab-ci-companion`'s pipeline tool window already ship, both
  verified 6/6 Compatible) groups modules by topological depth and
  lists each module's dependencies as text (`:app  ->  :libs:core`) —
  every node and every edge in the graph is fully represented, just
  without drawn lines. Cycles get their own dedicated, clearly labeled
  section at the top, in a distinct color, rather than requiring the
  reader to visually trace lines to notice a loop.

The layered list won on proven precedent and on making cycles
impossible to miss, not because a graphical diagram would be wrong in
principle — a richer visual layout is a natural direction for a future
version, not v0.1. The transformation from raw graph to layered view
(`ModuleGraphPresenter`) is deliberately Swing-free, so a future
graphical renderer could consume the exact same layered/cycle data
without redoing the graph logic.

## Why built this way

- **No custom `FileType`/`Language` registered for Gradle DSL.** A
  hand-rolled regex scanner over `settings.gradle(.kts)`/
  `build.gradle(.kts)` plain text is sufficient for what v0.1 needs
  (module declarations, `project(...)` references) — no completion, no
  syntax highlighting, no PSI tree required. Same "hand-roll over new
  dependency, small stable surface" principle already proven in this
  catalog (`CONSTITUTION.md` §6): `NginxLexer` for nginx config,
  `DockerfileParser` for Dockerfiles, now this for the one Gradle DSL
  statement shape this plugin needs.
- **Maven POMs parsed with the JDK's own XML DOM parser**, per the
  task's own guidance — real, well-formed XML doesn't need a
  hand-rolled parser the way Gradle's DSL does. External entity
  resolution and DOCTYPE declarations are explicitly disabled while
  parsing (`disallow-doctype-decl`) — a `pom.xml` found inside the
  user's own project has no legitimate reason to need XXE resolution
  for module/dependency-coordinate extraction, same "don't trust file
  content to reach out over the network" discipline as every other
  static-analysis parser in this catalog.
- **No Gradle, Maven, Java, Kotlin, or XML bundled-plugin dependency
  at all — `com.intellij.modules.platform` only.** Because both
  parsers read files as plain text/JDK-XML (never a bundled build
  system's own PSI), this plugin has zero platform-plugin dependency
  and works identically in every IntelliJ-family IDE this catalog
  targets, including IntelliJ IDEA Community.
- **Heavy computation off the EDT.** Walking the project's build files
  and computing the graph runs on a pooled thread
  (`executeOnPooledThread`); only the final `Tree` update happens on
  the EDT (`SwingUtilities.invokeLater`) — same discipline as every
  highlighting pass and tool window in this catalog
  (`CONSTITUTION.md` §6).
- **Manual refresh, not a file-change listener wired to every
  keystroke.** The graph only meaningfully changes when a module
  dependency declaration is added, removed, or edited *and saved* —
  re-walking every module's build file on every document change would
  be wasted, EDT-adjacent work for a result that usually doesn't
  change. A toolbar Refresh action costs nothing extra given the
  analysis already runs off the EDT.
- **Gradle detection takes priority over Maven** when a project
  somehow has both a `settings.gradle(.kts)` and a stray `pom.xml` at
  its root (e.g. leftover from a partial migration) — the presence of
  a real Gradle settings file is a stronger, more specific signal of
  "this is the actual build system for the project as a whole" than
  merely finding *a* `pom.xml` file somewhere.

## v0.1 scope

Free, all of it — no paywall, nothing held back for a future tier.
Deferred to a possible future v0.2 (not started, not promised):
multi-project/workspace analysis across several repositories at once,
and exporting the graph to an image or Mermaid diagram for
documentation.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us
at **gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
