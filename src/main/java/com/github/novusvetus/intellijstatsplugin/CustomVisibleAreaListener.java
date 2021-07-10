package com.github.novusvetus.intellijstatsplugin;

import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        FileDocumentManager instance = FileDocumentManager.getInstance();
        VirtualFile file = instance.getFile(visibleAreaEvent.getEditor().getDocument());
        Project project = visibleAreaEvent.getEditor().getProject();
        IntelliJStatsPlugin.appendHeartbeat(file, project, false);
    }
}
