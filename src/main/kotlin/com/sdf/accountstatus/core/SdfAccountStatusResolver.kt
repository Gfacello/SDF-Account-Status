package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal interface ProjectJsonDataSource {
    fun exists(path: Path): Boolean
    fun read(path: Path): String
    fun lastModifiedMillis(path: Path): Long
}

internal object NioProjectJsonDataSource : ProjectJsonDataSource {
    override fun exists(path: Path): Boolean = Files.exists(path)

    override fun read(path: Path): String = path.readText()

    override fun lastModifiedMillis(path: Path): Long = Files.getLastModifiedTime(path).toMillis()
}

internal class SdfAccountStatusResolver(
    private val projectJsonPath: Path?,
    private val dataSource: ProjectJsonDataSource = NioProjectJsonDataSource
) {
    fun resolve(): AccountWidgetState {
        val path = projectJsonPath
            ?: return AccountWidgetState(
                text = "no project",
                tooltip = "Project path not available",
                tone = WidgetTone.ERROR
            )

        if (!dataSource.exists(path)) {
            return AccountWidgetState(
                text = "project.json missing",
                tooltip = "project.json was not found at ${path.toAbsolutePath()}",
                tone = WidgetTone.ERROR
            )
        }

        val content = runCatching { dataSource.read(path) }.getOrElse {
            return AccountWidgetState(
                text = "read error",
                tooltip = "Cannot read ${path.toAbsolutePath()}: ${it.message}",
                tone = WidgetTone.ERROR
            )
        }

        val account = SdfProjectJsonParser.parseDefaultAuthId(content)
        if (account.isNullOrBlank()) {
            return AccountWidgetState(
                text = "defaultAuthId not set",
                tooltip = "Set defaultAuthId in project.json",
                tone = WidgetTone.WARNING
            )
        }

        val environment = SdfAccountEnvironmentClassifier.classify(account)
        val tone = when (environment) {
            AccountEnvironment.SANDBOX -> WidgetTone.OK
            AccountEnvironment.PRODUCTION -> WidgetTone.ERROR
            AccountEnvironment.UNKNOWN -> WidgetTone.WARNING
        }

        return AccountWidgetState(
            text = account,
            tooltip = "Current SDF default account ($account) - ${environment.label}. Click to open project.json",
            tone = tone,
            showCriticalIcon = environment == AccountEnvironment.PRODUCTION
        )
    }

    fun getLastModifiedMillisOrMinusOne(): Long {
        val path = projectJsonPath ?: return -1L
        if (!dataSource.exists(path)) return -1L
        return runCatching { dataSource.lastModifiedMillis(path) }.getOrDefault(-1L)
    }
}
