import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.sdf"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))

    intellijPlatform {
        webstorm("2026.1")
        bundledPlugin("JavaScript")
        pluginVerifier()
        zipSigner()
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }

        description = """
            <p>Shows the active NetSuite SuiteCloud Development Framework (SDF) account from <code>project.json</code> directly in the WebStorm status bar.</p>
            <ul>
              <li>Auto-detects <code>defaultAuthId</code> (and legacy <code>DefaultAuthID</code>)</li>
              <li>Auto-refreshes when the account changes in SuiteCloud settings</li>
              <li>Click the label to open <code>project.json</code></li>
            </ul>
            <h3>Environment Colors</h3>
            <ul>
              <li><b>Green</b>: Sandbox</li>
              <li><b>Red + critical icon</b>: Production</li>
              <li><b>Yellow</b>: Unknown</li>
            </ul>
            <p><small>Independent project; not affiliated with or endorsed by Oracle.</small></p>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>Adds compatibility with WebStorm 2026.2 and future IDE builds</li>
              <li>Removes deprecated status-bar API references from the compiled plugin</li>
              <li>Makes the NetSuite and WebStorm focus explicit in the Marketplace description</li>
            </ul>
        """.trimIndent()
    }

    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES
        )
        subsystemsToCheck = VerifyPluginTask.Subsystems.WITHOUT_ANDROID

        ides {
            create(IntelliJPlatformType.WebStorm, "2026.1")
            create(IntelliJPlatformType.WebStorm, "2026.2")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        jvmArgs("-Xmx2048m")
    }
}
