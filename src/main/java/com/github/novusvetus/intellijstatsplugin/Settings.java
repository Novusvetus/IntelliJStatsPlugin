package com.github.novusvetus.intellijstatsplugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Settings extends DialogWrapper {
    private final JPanel panel;
    private final JLabel apiPathLabel;
    private final JTextField apiPath;
    private final JLabel debugLabel;
    private final JCheckBox debug;

    public Settings(@Nullable Project project) {
        super(project, true);
        setTitle("IntelliJStatsPlugin Settings");
        setOKButtonText("Save");
        panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2));

        apiPathLabel = new JLabel("API Path:", JLabel.CENTER);
        panel.add(apiPathLabel);
        apiPath = new JTextField();
        String p = ConfigFile.get("settings", "apipath");
        if (p == null) p = "";
        apiPath.setText(p);
        panel.add(apiPath);

        debugLabel = new JLabel("Debug:", JLabel.CENTER);
        panel.add(debugLabel);
        String debugValue = ConfigFile.get("settings", "debug");
        debug = new JCheckBox();
        debug.setSelected(debugValue != null && debugValue.trim().equalsIgnoreCase("true"));
        panel.add(debug);

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected ValidationInfo doValidate() {
        return null;
    }

    @Override
    public void doOKAction() {
        ConfigFile.set("settings", "apipath", apiPath.getText());
        ConfigFile.set("settings", "debug", debug.isSelected() ? "true" : "false");
        IntelliJStatsPlugin.setupDebugging();
        IntelliJStatsPlugin.setLoggingLevel();
        super.doOKAction();
    }

}
