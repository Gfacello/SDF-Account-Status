package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.sdf.accountstatus.core.ProjectJsonUpdateResult
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IntellijProjectJsonGatewayTest {
    private lateinit var fixture: IdeaProjectTestFixture
    private lateinit var directory: Path
    private lateinit var path: Path
    private lateinit var file: VirtualFile
    private lateinit var document: Document
    private val original = """{"defaultAuthId":"original","name":"Project"}"""

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Account document gateway").fixture
        fixture.setUp()
        directory = Files.createTempDirectory("sdf-document-test")
        path = directory.resolve("project.json")
        Files.writeString(path, original)
        onEdt {
            file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
            document = FileDocumentManager.getInstance().getDocument(file)!!
        }
    }

    @AfterEach
    fun tearDown() {
        try {
            onEdt {
                WriteCommandAction.runWriteCommandAction(fixture.project) { file.isWritable = true }
                document.setReadOnly(false)
                WriteCommandAction.runWriteCommandAction(fixture.project) {
                    document.setText(original)
                    FileDocumentManager.getInstance().saveDocument(document)
                }
            }
        } finally {
            onEdt { fixture.tearDown() }
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `successful switch persists live document including unrelated unsaved edits`() = onEdt {
        val edited = original.replace("Project", "Unsaved project name")
        WriteCommandAction.runWriteCommandAction(fixture.project) { document.setText(edited) }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        val result = gateway().update("sandbox")
        assertIs<ProjectJsonUpdateResult.Updated>(result)
        assertEquals(edited.replace("original", "sandbox"), document.text)
        assertEquals(document.text, Files.readString(path))
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    @Test
    fun `read-only document rejects account switch without changing document or disk`() = onEdt {
        document.setReadOnly(true)
        val result = gateway().update("sandbox")
        assertIs<ProjectJsonUpdateResult.Invalid>(result)
        assertTrue(result.message.contains("read-only"))
        assertEquals(original, document.text)
        assertEquals(original, Files.readString(path))
    }

    @Test
    fun `read-only file rejects account switch without changing document or disk`() = onEdt {
        WriteCommandAction.runWriteCommandAction(fixture.project) { file.isWritable = false }
        val result = gateway().update("sandbox")
        assertIs<ProjectJsonUpdateResult.Invalid>(result)
        assertTrue(result.message.contains("read-only"))
        assertEquals(original, document.text)
        assertEquals(original, Files.readString(path))
    }

    @Test
    fun `failed save restores live unsaved edits and leaves disk untouched`() = onEdt {
        val edited = original.replace("Project", "Unsaved name")
        WriteCommandAction.runWriteCommandAction(fixture.project) { document.setText(edited) }
        val result = gateway { throw IllegalStateException("synthetic save failure") }.update("sandbox")
        assertIs<ProjectJsonUpdateResult.Invalid>(result)
        assertEquals(edited, document.text)
        assertEquals(original, Files.readString(path))
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    @Test
    fun `save returning without persistence is rejected and rolled back`() = onEdt {
        val result = gateway { }.update("sandbox")
        assertIs<ProjectJsonUpdateResult.Invalid>(result)
        assertEquals(original, document.text)
        assertEquals(original, Files.readString(path))
    }

    @Test
    fun `rollback preserves newer document changes made by a save listener`() = onEdt {
        val changed = """{"defaultAuthId":"listener","name":"Newer content"}"""
        val result = gateway {
            it.setText(changed)
            throw IllegalStateException("synthetic save listener failure")
        }.update("sandbox")
        assertIs<ProjectJsonUpdateResult.Invalid>(result)
        assertEquals(changed, document.text)
        assertEquals(original, Files.readString(path))
    }

    @Test
    fun `saved listener change to default is not reported as selected account success`() = onEdt {
        val changed = """{"defaultAuthId":"listener","name":"Newer content"}"""
        val result = gateway {
            it.setText(changed)
            FileDocumentManager.getInstance().saveDocument(it)
        }.update("sandbox")
        assertIs<ProjectJsonUpdateResult.Invalid>(result)
        assertEquals(changed, document.text)
        assertEquals(changed, Files.readString(path))
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    @Test
    fun `save listener may persist unrelated edits while preserving selected default`() = onEdt {
        val result = gateway {
            it.setText(it.text.replace("Project", "Listener updated name"))
            FileDocumentManager.getInstance().saveDocument(it)
        }.update("sandbox")
        assertIs<ProjectJsonUpdateResult.Updated>(result)
        assertEquals("""{"defaultAuthId":"sandbox","name":"Listener updated name"}""", document.text)
        assertEquals(document.text, Files.readString(path))
    }

    @Test
    fun `cancellation rolls back and propagates to the platform`() = onEdt {
        assertFailsWith<ProcessCanceledException> {
            gateway { throw ProcessCanceledException() }.update("sandbox")
        }
        assertEquals(original, document.text)
        assertEquals(original, Files.readString(path))
    }

    private fun gateway(save: ((Document) -> Unit)? = null): IntellijProjectJsonGateway =
        if (save == null) IntellijProjectJsonGateway(fixture.project, path)
        else IntellijProjectJsonGateway(fixture.project, path, save)

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action() else application.invokeAndWait(action, ModalityState.nonModal())
    }
}
