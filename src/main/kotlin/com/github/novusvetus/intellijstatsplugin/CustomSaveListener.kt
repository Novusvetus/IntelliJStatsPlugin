package com.github.novusvetus.intellijstatsplugin

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFile

class CustomSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val instance = FileDocumentManager.getInstance()
        val file = instance.getFile(document)
        IntelliJStatsPlugin.appendHeartbeat(file, IntelliJStatsPlugin.getProject(document), true)
    }

    override fun beforeAllDocumentsSaving() {}
    override fun beforeFileContentReload(file: VirtualFile, document: Document) {}
    override fun fileWithNoDocumentChanged(file: VirtualFile) {}
    override fun fileContentReloaded(file: VirtualFile, document: Document) {}
    override fun fileContentLoaded(file: VirtualFile, document: Document) {}
    override fun unsavedDocumentsDropped() {}
}