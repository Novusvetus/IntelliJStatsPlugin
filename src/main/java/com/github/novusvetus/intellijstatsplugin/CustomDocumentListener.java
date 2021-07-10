package com.github.novusvetus.intellijstatsplugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        Document document = documentEvent.getDocument();
        FileDocumentManager instance = FileDocumentManager.getInstance();
        VirtualFile file = instance.getFile(document);
        IntelliJStatsPlugin.appendHeartbeat(file, IntelliJStatsPlugin.getProject(document), false);
    }
}