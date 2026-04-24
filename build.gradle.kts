plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.15.0"
}

group = "com.sdf"
version = "0.1.0"

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
            untilBuild = "261.*"
        }

        description = """
            <h2>Overview</h2>
            <p>Displays the active SuiteCloud SDF default account from <code>project.json</code> directly in the status bar.</p>
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
        """.trimIndent()
        changeNotes = "Initial public release with account environment highlighting and auto-refresh support."
    }

    pluginVerification {
        ides {
            recommended()
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
