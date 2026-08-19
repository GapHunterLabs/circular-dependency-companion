# Privacy Policy — Circular Dependency Companion

**Effective date:** 2026-08-19

Circular Dependency Companion is a Gap Hunter Labs plugin for
IntelliJ Platform IDEs. This policy is short because the plugin's
design makes it short: there is nothing to disclose beyond what's
below.

## What this plugin collects

**Nothing.** Circular Dependency Companion does not collect, store,
transmit, or sell any data — no source code, no file contents, no file
paths, no usage analytics, no telemetry, no crash reports, no
personally identifiable information. Build file text
(`settings.gradle(.kts)`, `build.gradle(.kts)`, `pom.xml`) read from
your local project exists only in memory for as long as the IDE is
open, and only long enough to compute the module dependency graph.

## Network access

**None.** Circular Dependency Companion makes zero network calls
during normal operation. Every module and dependency shown is parsed
directly from build files already present on your local disk — no
Gradle daemon evaluation, no Maven reactor build, no remote repository
lookup, ever.

## Third parties

None. Circular Dependency Companion has no third-party SDKs, no
analytics libraries, no ad networks, no external dependencies that
phone home. Maven POM parsing uses only the JDK's own bundled
`javax.xml.parsers`.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the plugin's `CHANGELOG.md`.

## Contact

Questions about this policy: **gaphunterlabs@gmail.com**
