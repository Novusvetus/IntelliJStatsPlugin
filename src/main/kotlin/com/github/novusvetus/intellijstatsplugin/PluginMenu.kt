package com.github.novusvetus.intellijstatsplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class PluginMenu : AnAction("IntelliJStatsPlugin Settings") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val popup = Settings(project)
        popup.show()
    }
}