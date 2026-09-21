import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.sdf"
version = "0.2.0"

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
            <p>Shows the active NetSuite SuiteCloud Development Framework (SDF) authentication ID in the WebStorm status bar and lets you switch configured accounts without leaving the IDE.</p>
            <ul>
              <li>Reads <code>defaultAuthId</code> and legacy <code>DefaultAuthID</code></li>
              <li>Loads configured authentication IDs with the read-only <code>sdfcli manageauth -list</code> operation</li>
              <li>Searches accounts and groups them under a combined account-family/customer row</li>
              <li>Confirms production switches and preserves unrelated <code>project.json</code> content</li>
              <li>Keeps <code>Open project.json</code> fixed in the popup footer</li>
            </ul>
            <p>The plugin does not read SuiteCloud credential contents, log raw CLI output, or send automatic telemetry. Exception reports are sent to JetBrains Marketplace only when the user explicitly submits one.</p>
            <p><small>Independent project; not affiliated with or endorsed by Oracle.</small></p>
        """.trimIndent()

        changeNotes = """
            <p><b>Version 0.2.0</b></p>
            <ul>
              <li>Adds a searchable account picker backed by the read-only SuiteCloud CLI account-list command</li>
              <li>Searches authentication IDs, account names, account numbers, roles, and environments</li>
              <li>Groups accounts under combined account-family/customer rows in collapsible, aligned sections</li>
              <li>Highlights the current account, recommends related environments, and confirms production switches</li>
              <li>Uses a compact, resizable chooser with a fixed <code>Open project.json</code> action</li>
              <li>Safely updates <code>project.json</code> while preserving unrelated JSON values</li>
              <li>Fixes the Swing accessibility crash seen when opening the chooser</li>
              <li>Adds opt-in JetBrains Marketplace exception reporting and production documentation</li>
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
    named<Jar>("jar") {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
        }
    }

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
