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

internal data class ProjectJsonSnapshot(
    val authenticationId: String?,
    val problem: AccountWidgetState?,
    val lastModifiedMillis: Long,
    val changedWhileReading: Boolean = false
) {
    companion object {
        fun configured(authenticationId: String, lastModifiedMillis: Long = Long.MIN_VALUE) =
            ProjectJsonSnapshot(authenticationId, null, lastModifiedMillis)
    }
}

internal class SdfAccountStatusResolver(
    private val projectJsonPath: Path?,
    private val dataSource: ProjectJsonDataSource = NioProjectJsonDataSource
) {
    fun readSnapshot(): ProjectJsonSnapshot {
        val path = projectJsonPath ?: return problem("no project", "Project path not available")
        val exists = runCatching { dataSource.exists(path) }.getOrElse {
            return problem("read error", "Cannot access ${path.toAbsolutePath()}: ${it.message}")
        }
        if (!exists) return problem("project.json missing", "project.json was not found at ${path.toAbsolutePath()}")

        // Capture both stamps around the single content read. A changed read is retried, not published.
        val before = getLastModifiedMillisOrMinusOne()
        val content = runCatching { dataSource.read(path) }.getOrElse {
            return problem("read error", "Cannot read ${path.toAbsolutePath()}: ${it.message}", stamp = before)
        }
        val after = getLastModifiedMillisOrMinusOne()
        val snapshot = when (val parsed = SdfProjectJsonParser.inspect(content)) {
            is ProjectJsonParseResult.Configured -> ProjectJsonSnapshot.configured(parsed.authenticationId, before)
            ProjectJsonParseResult.MissingKey -> problem("defaultAuthId not set", "Set defaultAuthId in project.json", WidgetTone.WARNING, before)
            ProjectJsonParseResult.InvalidJson -> problem("project.json invalid", "project.json must contain a valid JSON object", stamp = before)
            is ProjectJsonParseResult.InvalidKeyType -> problem("defaultAuthId invalid", "${parsed.key} must be a non-empty JSON string in project.json", stamp = before)
            is ProjectJsonParseResult.BlankAuthenticationId -> problem("defaultAuthId empty", "${parsed.key} must be a non-empty JSON string in project.json", WidgetTone.WARNING, before)
        }
        return snapshot.copy(changedWhileReading = before != after)
    }

    /** Compatibility entry point for callers interested only in the presentation. */
    fun resolve(knownEnvironments: Map<String, AccountEnvironment> = emptyMap()): AccountWidgetState {
        val snapshot = readSnapshot()
        snapshot.problem?.let { return it }
        val id = requireNotNull(snapshot.authenticationId)
        return SdfAccountStatusPresentation.forAuthentication(id, knownEnvironments[id] ?: AccountEnvironment.UNKNOWN)
    }

    fun getLastModifiedMillisOrMinusOne(): Long {
        val path = projectJsonPath ?: return -1L
        return runCatching {
            if (!dataSource.exists(path)) -1L else dataSource.lastModifiedMillis(path)
        }.getOrDefault(-1L)
    }

    private fun problem(
        text: String,
        tooltip: String,
        tone: WidgetTone = WidgetTone.ERROR,
        stamp: Long = -1L
    ) = ProjectJsonSnapshot(null, AccountWidgetState(text, tooltip, tone), stamp)
}
