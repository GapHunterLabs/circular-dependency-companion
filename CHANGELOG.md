<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Circular Dependency Companion Changelog

## [Unreleased]

## [0.1.1]

### Fixed

- Tool window no longer shows the generic platform icon in the sidebar —
  the real Gap Hunter Labs mark is now declared via `icon=` on
  `<toolWindow>`.

## [0.1.0]

### Added

- **Module dependency graph visualization** for multi-module Gradle and
  Maven projects, in a "Module Dependencies" tool window: modules
  grouped into layers by dependency depth, each entry showing what it
  depends on.
- **Real cycle detection** (DFS with three-color marking) with the
  exact cycle path shown in its own "Cycles detected" section, colored
  distinctly -- never silently mixed into the regular layer list.
- **Gradle support**: parses `settings.gradle(.kts)` for declared
  `include(...)` modules and each module's own `build.gradle(.kts)` for
  `project(":...")` dependency references across the common
  configurations (`implementation`, `api`, `compileOnly`,
  `testImplementation`, and others) -- both Groovy and Kotlin DSL.
- **Maven support**: parses each `pom.xml`'s `<modules>` and
  cross-references `<dependency>` entries whose `groupId:artifactId`
  matches another module discovered in the same project tree,
  including nested multi-module POMs.
- **Honest single-module handling**: a project with no submodules
  declared shows "no cycles possible: single-module project" instead
  of an empty, unexplained tool window.
- 100% static text/XML analysis -- no Gradle daemon, no Maven reactor
  build, no network call.

[Unreleased]: https://github.com/GapHunterLabs/circular-dependency-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/circular-dependency-companion/commits/0.1.0
