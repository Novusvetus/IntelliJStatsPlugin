package com.github.novusvetus.intellijstatsplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import java.awt.GridLayout
import javax.swing.*

class Settings(project: Project?) : DialogWrapper(project, true) {
    private val panel: JPanel
    private val apiPathLabel: JLabel
    private val apiPath: JTextField
    private val debugLabel: JLabel
    private val debug: JCheckBox
    override fun createCenterPanel(): JComponent? {
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        return null
    }

    public override fun doOKAction() {
        ConfigFile["settings", "apipath"] = apiPath.text
        ConfigFile["settings", "debug"] = if (debug.isSelected) "true" else "false"
        IntelliJStatsPlugin.setupDebugging()
        IntelliJStatsPlugin.setLoggingLevel()
        super.doOKAction()
    }

    init {
        title = "IntelliJStatsPlugin Settings"
        setOKButtonText("Save")
        panel = JPanel()
        panel.layout = GridLayout(0, 2)
        apiPathLabel = JLabel("API Path:", JLabel.CENTER)
        panel.add(apiPathLabel)
        apiPath = JTextField()
        var p = ConfigFile["settings", "apipath"]
        if (p == null) p = ""
        apiPath.text = p
        panel.add(apiPath)
        debugLabel = JLabel("Debug:", JLabel.CENTER)
        panel.add(debugLabel)
        val debugValue = ConfigFile["settings", "debug"]
        debug = JCheckBox()
        debug.isSelected = debugValue != null && debugValue.trim { it <= ' ' }.equals("true", ignoreCase = true)
        panel.add(debug)
        init()
    }
}