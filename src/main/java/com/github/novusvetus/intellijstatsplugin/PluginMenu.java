package com.github.novusvetus.intellijstatsplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

public class PluginMenu extends AnAction {
    public PluginMenu() {
        super("IntelliJStatsPlugin Settings");
    }

    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        Settings popup = new Settings(project);
        popup.show();
    }
}
