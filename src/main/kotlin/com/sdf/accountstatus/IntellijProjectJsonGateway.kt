package com.sdf.accountstatus

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sdf.accountstatus.core.ProjectJsonUpdateResult
import com.sdf.accountstatus.core.SdfProjectJsonUpdater
import java.nio.file.Path

/** Changes the live editor document, then publishes success only after persistence succeeds. */
internal class IntellijProjectJsonGateway(
    private val project: Project,
    private val path: Path?,
    private val saveDocument: (Document) -> Unit = { FileDocumentManager.getInstance().saveDocument(it) }
) : ProjectJsonWriter {
    fun findFile(): VirtualFile? = path?.let { LocalFileSystem.getInstance().findFileByNioFile(it) }

    override fun update(authenticationId: String): ProjectJsonUpdateResult {
        if (project.isDisposed) return invalid("The project is closed.")
        val file = findFile() ?: return invalid("project.json was not found.")
        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(file) ?: return invalid("project.json could not be opened.")
        if (!file.isWritable || !document.isWritable) return invalid("project.json is read-only.")

        // Use the live document so unrelated unsaved edits survive account switching.
        val original = document.text
        val result = SdfProjectJsonUpdater.updateDefaultAuthId(original, authenticationId)
        if (result !is ProjectJsonUpdateResult.Updated) return result
        return try {
            write("Select NetSuite SDF Account") {
                document.setText(result.content)
                saveDocument(document)
            }
            check(!manager.isDocumentUnsaved(document)) { "project.json was not saved" }
            result
        } catch (exception: Exception) {
            // Save listeners may have changed the document. Never discard their newer content.
            if (document.text == result.content) {
                try {
                    write("Restore project.json") { document.setText(original) }
                } catch (cancelled: ProcessCanceledException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Preserve the original failure; a read-only document may prevent rollback.
                }
            }
            if (exception is ProcessCanceledException) throw exception
            invalid("Unable to update project.json.")
        }
    }

    private fun write(name: String, action: () -> Unit) {
        WriteCommandAction.writeCommandAction(project).withName(name).run<RuntimeException>(action)
    }

    private fun invalid(reason: String) = ProjectJsonUpdateResult.Invalid("$reason The account was not changed.")
}
