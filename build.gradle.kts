import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.sdf"
version = "0.3.1-dev.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))
    // IntelliJ platform fixtures and its Jupiter session listener still use JUnit 3/4 classes.
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        webstorm("2026.1")
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
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
            <p>Recognize, choose, and open your NetSuite SuiteCloud Development Framework (SDF) accounts directly from WebStorm.</p>
            <ul>
              <li>Show the authentication ID or company, environment, and role in the status bar</li>
              <li>Search and filter accounts by environment and role, with current and related accounts grouped together</li>
              <li>Refresh accounts without closing the picker or losing valid filters</li>
              <li>Open the current or selected account using a locally saved HTTPS NetSuite UI URL</li>
              <li>Start account setup through Oracle's WebStorm Account Management settings</li>
              <li>Confirm production switches and preserve unrelated <code>project.json</code> content</li>
            </ul>
            <p><b>Requirements:</b> Account discovery uses an existing SuiteCloud CLI for Node.js installation and its supported Node.js/JDK runtimes. Legacy Java CLI discovery is available as an explicit choice. Account setup requires Oracle's SuiteCloud IDE Plug-in for WebStorm. The authentication ID from <code>project.json</code> remains visible when account discovery is unavailable.</p>
            <p>The plugin does not read SuiteCloud credential contents, log raw CLI output, or send automatic telemetry. The IDE may submit exception reports to JetBrains Marketplace manually or automatically according to its error-reporting settings.</p>
            <p><small>Independent project; not affiliated with or endorsed by Oracle.</small></p>
        """.trimIndent()

        changeNotes = """
            <p><b>Development build 0.3.1-dev.1</b></p>
            <ul>
              <li>Show an account-loading progress bar with completed and total account counts</li>
              <li>Load account details two at a time to reduce discovery delays</li>
              <li>Recover from unexpected account-provider errors with retry guidance</li>
            </ul>
            <p><b>Version 0.3.0</b></p>
            <ul>
              <li>Refresh accounts in place and combine environment, role, and text filters</li>
              <li>Choose a persistent detailed status display with complete account tooltips</li>
              <li>Open an account in NetSuite through a locally configured account URL</li>
              <li>Start Add an account through Oracle's Account Management settings</li>
              <li>Use the Node.js SuiteCloud CLI, with local path settings and an explicit legacy provider option</li>
              <li>Preserve filters after cancelled production confirmation and reject stale refresh results</li>
              <li>Improve document-save verification, accessibility, and workflow regression coverage</li>
              <li>Clarify IDE-controlled exception reporting</li>
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
