import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.2.6.2")

        // v0.1 needs no bundled-language-plugin dependency at all, same
        // "com.intellij.modules.platform only" pattern as
        // dockerfile-layer-size-companion: Gradle settings/build files are
        // read as plain text (hand-rolled regex parser, never Groovy/
        // Kotlin-DSL PSI), and Maven POMs are read with the JDK's own
        // javax.xml.parsers (DOM), never a bundled Maven/XML IDE plugin.
        // This also means it works identically in IntelliJ IDEA Community,
        // which is the lowest common denominator across this catalog's
        // verifyPlugin target IDEs.
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 243 = 2024.3, so as not to exclude the real installed base.
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }

    // Same tooling bug as every other Gap Hunter Labs plugin (Gradle 9.5 +
    // IntelliJ Platform Gradle Plugin 2.16 + IDE 2025.2.6.2): the
    // bytecode instrumenter fails with "instrumentIdeaExtensions
    // doesn't support the nested element". Not required for
    // build/test/verifyPlugin.
    instrumentCode = false

    // Catch experimental/internal API usage locally, before Marketplace's
    // own verifier flags it post-upload. Never relax this list without a
    // documented exception.
    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        )
    }

    // publishPlugin credentials -- token/cert/key read from
    // ~/.gradle/gradle.properties (self-signed cert generated once for
    // the whole catalog, 10-year validity) -- never in this file.
    publishing {
        token.set(providers.gradleProperty("gapHunterLabs.marketplace.token"))
    }

    signing {
        certificateChain.set(providers.gradleProperty("gapHunterLabs.marketplace.certificateChain"))
        privateKey.set(providers.gradleProperty("gapHunterLabs.marketplace.privateKey"))
        password.set(providers.gradleProperty("gapHunterLabs.marketplace.privateKeyPassword"))
    }
}
