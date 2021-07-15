package com.github.novusvetus.intellijstatsplugin

import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager

class CustomVisibleAreaListener : VisibleAreaListener {
    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        val instance = FileDocumentManager.getInstance()
        val file = instance.getFile(visibleAreaEvent.editor.document)
        val project = visibleAreaEvent.editor.project
        IntelliJStatsPlugin.appendHeartbeat(file, project, false)
    }
}