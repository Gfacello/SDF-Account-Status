package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.WidgetTone
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdfAccountStatusResolverTest {
    private val path = Path.of("/tmp/project.json")

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

    private class FakeDataSource(
        private val exists: Boolean,
        private val content: String = "",
        private val readError: Throwable? = null,
        private val lastModifiedMillis: Long = -1L
    ) : ProjectJsonDataSource {
        override fun exists(path: Path): Boolean = exists

        override fun read(path: Path): String {
            readError?.let { throw it }
            return content
        }

        override fun lastModifiedMillis(path: Path): Long = lastModifiedMillis
    }
}
