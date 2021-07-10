package com.github.novusvetus.intellijstatsplugin;

import com.intellij.AppTopics;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class IntelliJStatsPlugin implements ApplicationComponent {

    public static final BigDecimal FREQUENCY = new BigDecimal(2 * 60); // max secs between heartbeats for continuous coding
    public static final Logger log = Logger.getInstance("IntelliJStatsPlugin");
    private static final ConcurrentLinkedQueue<Heartbeat> heartbeatsQueue = new ConcurrentLinkedQueue<Heartbeat>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static String VERSION;
    public static String IDE_NAME;
    public static String IDE_VERSION;
    public static MessageBusConnection connection;
    public static Boolean DEBUG = false;
    public static Boolean DEBUG_CHECKED = false;
    public static Boolean READY = false;
    public static String lastFile = null;
    public static BigDecimal lastTime = new BigDecimal(0);
    private static ScheduledFuture<?> scheduledFixture;
    private final int queueTimeoutSeconds = 30;

    public IntelliJStatsPlugin() {
    }

    public static void setupDebugging() {
        String debug = ConfigFile.get("settings", "debug");
        IntelliJStatsPlugin.DEBUG = debug != null && debug.trim().equals("true");
    }

    public static void setLoggingLevel() {
        if (IntelliJStatsPlugin.DEBUG) {
            log.setLevel(Level.DEBUG);
            log.debug("Logging level set to DEBUG");
        } else {
            log.setLevel(Level.INFO);
        }
    }

    public static void appendHeartbeat(final VirtualFile file, Project project, final boolean isWrite) {
        checkDebug();

        if (!shouldLogFile(file))
            return;
        final String projectName = project != null ? project.getName() : null;
        final BigDecimal time = IntelliJStatsPlugin.getCurrentTimestamp();
        if (!isWrite && file.getPath().equals(IntelliJStatsPlugin.lastFile) && !enoughTimePassed(time)) {
            return;
        }
        IntelliJStatsPlugin.lastFile = file.getPath();
        IntelliJStatsPlugin.lastTime = time;
        final String language = IntelliJStatsPlugin.getLanguage(file);
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                Heartbeat h = new Heartbeat();
                h.entity = file.getPath();
                h.timestamp = time;
                h.isWrite = isWrite;
                h.project = projectName;
                h.language = language;
                heartbeatsQueue.add(h);
            }
        });
    }

    public static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    private static void checkDebug() {
        if (DEBUG_CHECKED) return;
        DEBUG_CHECKED = true;
        if (!DEBUG) return;
        try {
            Messages.showWarningDialog("Your IDE may respond slower. Disable debug mode from Tools -> IntelliJStatsPlugin Settings.", "IntelliJStatsPlugin Debug Mode Enabled");
        } catch (Exception e) {
        }
    }

    public static boolean shouldLogFile(VirtualFile file) {
        if (file == null || file.getUrl().startsWith("mock://")) {
            return false;
        }
        String filePath = file.getPath();
        return !filePath.equals("atlassian-ide-plugin.xml") && !filePath.contains("/.idea/workspace.xml");
    }

    public static BigDecimal getCurrentTimestamp() {
        return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4, BigDecimal.ROUND_HALF_UP);
    }

    public static boolean enoughTimePassed(BigDecimal currentTime) {
        return IntelliJStatsPlugin.lastTime.add(FREQUENCY).compareTo(currentTime) < 0;
    }

    private static String getLanguage(final VirtualFile file) {
        FileType type = file.getFileType();
        if (type != null)
            return type.getName();
        return null;
    }

    private static void processHeartbeatQueue(@Nullable boolean all) {
        if (IntelliJStatsPlugin.READY) {

            ArrayList<Heartbeat> heartbeats = new ArrayList<Heartbeat>();

            int i = 0;
            while (true) {
                Heartbeat h = heartbeatsQueue.poll();
                if (h == null)
                    break;

                heartbeats.add(h);
                i = i + 1;

                if (i >= 10)
                    break;
            }

            sendHeartbeat(heartbeats, all);

            if (all && heartbeatsQueue.size() > 0) {
                processHeartbeatQueue(true);
            }
        }
    }

    private static void sendHeartbeat(final ArrayList<Heartbeat> heartbeats, Boolean all) {
        try {
            String p = ConfigFile.get("settings", "apipath");
            if (p == null) p = "";

            if (p != "") {
                if (heartbeats.size() > 0) {
                    String json = toJSON(heartbeats);
                    try {
                        URL url = new URL(p);
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("POST");
                        con.setRequestProperty("Content-Type", "application/json; utf-8");
                        con.setDoOutput(true);

                        try (OutputStream os = con.getOutputStream()) {
                            byte[] input = json.getBytes(StandardCharsets.UTF_8);
                            os.write(input, 0, input.length);
                        }

                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                            StringBuilder response = new StringBuilder();
                            String responseLine = null;
                            while ((responseLine = br.readLine()) != null) {
                                if (IntelliJStatsPlugin.DEBUG) {
                                    log.debug(responseLine);
                                }
                                response.append(responseLine.trim());
                            }

                            if (response.toString() == "success") {
                                return;
                            }
                        }

                    } catch (Exception e) {
                    }
                }

            }

        } catch (Exception e) {
            log.warn(e);
        }

        if (!all) {
            for (Heartbeat heartbeat : heartbeats) {
                heartbeatsQueue.add(heartbeat);
            }
        }
    }

    private static String toJSON(ArrayList<Heartbeat> heartbeats) {
        StringBuffer json = new StringBuffer();
        json.append("[");
        boolean first = true;
        for (Heartbeat heartbeat : heartbeats) {
            StringBuffer h = new StringBuffer();
            h.append("{\"entity\":\"");
            h.append(jsonEscape(heartbeat.entity));
            h.append("\",\"timestamp\":");
            h.append(heartbeat.timestamp.toPlainString());
            h.append(",\"is_write\":");
            h.append(heartbeat.isWrite.toString());
            if (heartbeat.project != null) {
                h.append(",\"project\":\"");
                h.append(jsonEscape(heartbeat.project));
                h.append("\"");
            }
            if (heartbeat.language != null) {
                h.append(",\"language\":\"");
                h.append(jsonEscape(heartbeat.language));
                h.append("\"");
            }
            h.append("}");
            if (!first)
                json.append(",");
            json.append(h);
            first = false;
        }
        json.append("]");
        return json.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null)
            return null;
        StringBuffer escaped = new StringBuffer();
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    boolean isUnicode = (c >= '\u0000' && c <= '\u001F') || (c >= '\u007F' && c <= '\u009F') || (c >= '\u2000' && c <= '\u20FF');
                    if (isUnicode) {
                        escaped.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int k = 0; k < 4 - hex.length(); k++) {
                            escaped.append('0');
                        }
                        escaped.append(hex.toUpperCase());
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    public void initComponent() {
        try {
            // support older IDE versions with deprecated PluginManager
            VERSION = PluginManager.getPlugin(PluginId.getId("com.github.novusvetus.intellijstatsplugin")).getVersion();
        } catch (Exception e) {
            // use PluginManagerCore if PluginManager deprecated
            VERSION = PluginManagerCore.getPlugin(PluginId.getId("com.github.novusvetus.intellijstatsplugin")).getVersion();
        }
        log.info("Initializing IntelliJStatsPlugin plugin v" + VERSION);

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix();
        IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();

        setupDebugging();
        setLoggingLevel();
        setupBase();
        setupEventListeners();
        setupQueueProcessor();
    }

    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {

                // save file
                MessageBus bus = ApplicationManager.getApplication().getMessageBus();
                connection = bus.connect();
                connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new CustomSaveListener());

                // edit document
                EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new CustomDocumentListener());

                // mouse press
                EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new CustomEditorMouseListener());

                // scroll document
                EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new CustomVisibleAreaListener());
            }
        });
    }

    private void setupQueueProcessor() {
        final Runnable handler = new Runnable() {
            public void run() {
                processHeartbeatQueue(false);
            }
        };
        long delay = queueTimeoutSeconds;
        scheduledFixture = scheduler.scheduleAtFixedRate(handler, delay, delay, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void setupBase() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                IntelliJStatsPlugin.READY = true;
                log.info("I'm ready.");
            }
        });
    }

    public void disposeComponent() {
        try {
            connection.disconnect();
        } catch (Exception e) {
        }
        try {
            scheduledFixture.cancel(true);
        } catch (Exception e) {
        }

        // make sure to send all heartbeats before exiting
        processHeartbeatQueue(true);
    }

    @NotNull
    public String getComponentName() {
        return "IntelliJStatsPlugin";
    }
}
