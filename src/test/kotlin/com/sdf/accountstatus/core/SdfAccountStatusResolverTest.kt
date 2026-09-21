package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.WidgetTone
import com.sdf.accountstatus.domain.AccountEnvironment
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdfAccountStatusResolverTest {
    private val path = Path.of("/tmp/project.json")

    @Test
    fun `returns no project state when a project path is unavailable`() {
        val state = SdfAccountStatusResolver(null).resolve()

        assertEquals("no project", state.text)
        assertEquals(WidgetTone.ERROR, state.tone)
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `returns missing file state when project json does not exist`() {
        val resolver = SdfAccountStatusResolver(path, FakeDataSource(exists = false))

        val state = resolver.resolve()

        assertEquals("project.json missing", state.text)
        assertEquals(WidgetTone.ERROR, state.tone)
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `returns read error state when project json cannot be read`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(exists = true, readError = IllegalStateException("permission denied"))
        )

        val state = resolver.resolve()

        assertEquals("read error", state.text)
        assertEquals(WidgetTone.ERROR, state.tone)
        assertTrue(state.tooltip.contains("permission denied"))
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `returns read error state when project json cannot be inspected`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(exists = true, existsError = IllegalStateException("access denied"))
        )

        val state = resolver.resolve()

        assertEquals("read error", state.text)
        assertEquals(WidgetTone.ERROR, state.tone)
        assertTrue(state.tooltip.contains("access denied"))
        assertFalse(state.showCriticalIcon)
        assertEquals(-1L, resolver.getLastModifiedMillisOrMinusOne())
    }

    @Test
    fun `returns missing defaultAuthId state when key does not exist`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(exists = true, content = """{"name":"demo"}""")
        )

        val state = resolver.resolve()

        assertEquals("defaultAuthId not set", state.text)
        assertEquals(WidgetTone.WARNING, state.tone)
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `returns invalid json state instead of reporting a missing key`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(exists = true, content = """{"defaultAuthId":}""")
        )

        val state = resolver.resolve()

        assertEquals("project.json invalid", state.text)
        assertEquals(WidgetTone.ERROR, state.tone)
        assertTrue(state.tooltip.contains("valid JSON object"))
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `returns invalid key state when defaultAuthId is not a string`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(exists = true, content = """{"defaultAuthId":123}""")
        )

        val state = resolver.resolve()

        assertEquals("defaultAuthId invalid", state.text)
        assertEquals(WidgetTone.ERROR, state.tone)
        assertTrue(state.tooltip.contains("non-empty JSON string"))
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `returns empty key state when defaultAuthId is blank`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(exists = true, content = """{"DefaultAuthID":"  "}""")
        )

        val state = resolver.resolve()

        assertEquals("defaultAuthId empty", state.text)
        assertEquals(WidgetTone.WARNING, state.tone)
        assertTrue(state.tooltip.contains("DefaultAuthID"))
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `uses CLI-derived environment instead of a misleading auth ID name`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(
                exists = true,
                content = """{"defaultAuthId":"misleading-auth-name"}"""
            )
        )

        val state = resolver.resolve(
            mapOf("misleading-auth-name" to AccountEnvironment.PRODUCTION)
        )

        assertEquals(WidgetTone.ERROR, state.tone)
        assertTrue(state.showCriticalIcon)
        assertTrue(state.tooltip.contains("Production"))
    }

    @Test
    fun `does not infer an environment from an unverified authentication ID`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(
                exists = true,
                content = """{"defaultAuthId":"claims-to-be-production"}"""
            )
        )

        val state = resolver.resolve()

        assertEquals(WidgetTone.WARNING, state.tone)
        assertFalse(state.showCriticalIcon)
        assertTrue(state.tooltip.contains(AccountEnvironment.UNKNOWN.label))
    }

    @Test
    fun `uses warning tone for CLI-derived release preview account`() {
        val resolver = SdfAccountStatusResolver(
            path,
            FakeDataSource(
                exists = true,
                content = """{"defaultAuthId":"demo-release-preview"}"""
            )
        )

        val state = resolver.resolve(
            mapOf("demo-release-preview" to AccountEnvironment.RELEASE_PREVIEW)
        )

        assertEquals(WidgetTone.WARNING, state.tone)
        assertFalse(state.showCriticalIcon)
        assertTrue(state.tooltip.contains("Release Preview"))
    }

    @Test
    fun `snapshot reads project content once and derives current ID from that content`() {
        var reads = 0
        val dataSource = object : ProjectJsonDataSource {
            override fun exists(path: Path) = true
            override fun read(path: Path): String {
                reads++
                return if (reads == 1) """{"defaultAuthId":"first"}""" else """{"defaultAuthId":"second"}"""
            }
            override fun lastModifiedMillis(path: Path) = 42L
        }
        val snapshot = SdfAccountStatusResolver(path, dataSource).readSnapshot()
        assertEquals(1, reads)
        assertEquals("first", snapshot.authenticationId)
        assertEquals("first", SdfAccountStatusPresentation.present(snapshot).text)
        assertFalse(snapshot.changedWhileReading)
    }

    @Test
    fun `snapshot detects edits during content read instead of recording a newer stamp as observed`() {
        var stamp = 1L
        val dataSource = object : ProjectJsonDataSource {
            override fun exists(path: Path) = true
            override fun read(path: Path): String {
                stamp = 2L
                return """{"defaultAuthId":"old"}"""
            }
            override fun lastModifiedMillis(path: Path) = stamp
        }
        val snapshot = SdfAccountStatusResolver(path, dataSource).readSnapshot()
        assertTrue(snapshot.changedWhileReading)
        assertEquals(1L, snapshot.lastModifiedMillis)
    }

    private class FakeDataSource(
        private val exists: Boolean,
        private val content: String = "",
        private val existsError: Throwable? = null,
        private val readError: Throwable? = null,
        private val lastModifiedMillis: Long = -1L
    ) : ProjectJsonDataSource {
        override fun exists(path: Path): Boolean {
            existsError?.let { throw it }
            return exists
        }

        override fun read(path: Path): String {
            readError?.let { throw it }
            return content
        }

        override fun lastModifiedMillis(path: Path): Long = lastModifiedMillis
    }
}
