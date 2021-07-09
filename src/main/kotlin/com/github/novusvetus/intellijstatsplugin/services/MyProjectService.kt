package com.github.novusvetus.intellijstatsplugin.services

import com.github.novusvetus.intellijstatsplugin.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
