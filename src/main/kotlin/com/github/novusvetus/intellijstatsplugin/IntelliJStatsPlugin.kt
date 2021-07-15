package com.github.novusvetus.intellijstatsplugin

import com.intellij.AppTopics
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.messages.MessageBusConnection
import org.apache.log4j.Level
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class IntelliJStatsPlugin : ApplicationComponent {
    private val queueTimeoutSeconds = 30
    override fun initComponent() {
        VERSION = try {
            // support older IDE versions with deprecated PluginManager
            PluginManager.getPlugin(PluginId.getId("com.github.novusvetus.intellijstatsplugin"))!!
                .version
        } catch (e: Exception) {
            // use PluginManagerCore if PluginManager deprecated
            PluginManagerCore.getPlugin(PluginId.getId("com.github.novusvetus.intellijstatsplugin"))!!
                .version
        }
        log.info("Initializing IntelliJStatsPlugin plugin v$VERSION")

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix()
        IDE_VERSION = ApplicationInfo.getInstance().fullVersion
        setupDebugging()
        setLoggingLevel()
        setupBase()
        setupEventListeners()
        setupQueueProcessor()
    }

    private fun setupEventListeners() {
        ApplicationManager.getApplication().invokeLater { // save file
            val bus = ApplicationManager.getApplication().messageBus
            connection = bus.connect()
            connection!!.subscribe(AppTopics.FILE_DOCUMENT_SYNC, CustomSaveListener())

            // edit document
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(CustomDocumentListener())

            // mouse press
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(CustomEditorMouseListener())

            // scroll document
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(CustomVisibleAreaListener())
        }
    }

    private fun setupQueueProcessor() {
        val handler = Runnable { processHeartbeatQueue(false) }
        val delay = queueTimeoutSeconds.toLong()
        scheduledFixture = scheduler.scheduleAtFixedRate(handler, delay, delay, TimeUnit.SECONDS)
    }

    private fun setupBase() {
        ApplicationManager.getApplication().executeOnPooledThread {
            READY = true
            log.info("I'm ready.")
        }
    }

    override fun disposeComponent() {
        try {
            connection!!.disconnect()
        } catch (e: Exception) {
        }
        try {
            scheduledFixture!!.cancel(true)
        } catch (e: Exception) {
        }

        // make sure to send all heartbeats before exiting
        processHeartbeatQueue(true)
    }

    override fun getComponentName(): String {
        return "IntelliJStatsPlugin"
    }

    companion object {
        private val FREQUENCY = BigDecimal(2 * 60) // max secs between heartbeats for continuous coding
        val log = Logger.getInstance("IntelliJStatsPlugin")
        private val heartbeatsQueue = ConcurrentLinkedQueue<Heartbeat>()
        private val scheduler = Executors.newScheduledThreadPool(1)
        var VERSION: String? = null
        var IDE_NAME: String? = null
        var IDE_VERSION: String? = null
        var connection: MessageBusConnection? = null
        private var DEBUG = false
        private var DEBUG_CHECKED = false
        var READY = false
        private var lastFile: String? = null
        private var lastTime = BigDecimal(0)
        private var scheduledFixture: ScheduledFuture<*>? = null
        fun setupDebugging() {
            val debug = ConfigFile["settings", "debug"]
            DEBUG = debug != null && debug.trim { it <= ' ' } == "true"
        }

        fun setLoggingLevel() {
            if (DEBUG) {
                log.setLevel(Level.DEBUG)
                log.debug("Logging level set to DEBUG")
            } else {
                log.setLevel(Level.INFO)
            }
        }

        fun appendHeartbeat(file: VirtualFile?, project: Project?, isWrite: Boolean) {
            checkDebug()
            if (!shouldLogFile(file)) return
            val projectName = project?.name
            val time = currentTimestamp
            if (!isWrite && file!!.path == lastFile && !enoughTimePassed(time)) {
                return
            }
            lastFile = file!!.path
            lastTime = time
            val language = getLanguage(file)
            ApplicationManager.getApplication().executeOnPooledThread {
                val h = Heartbeat()
                h.entity = file.path
                h.timestamp = time
                h.isWrite = isWrite
                h.project = projectName
                h.language = language
                heartbeatsQueue.add(h)
            }
        }

        fun getProject(document: Document?): Project? {
            val editors = EditorFactory.getInstance().getEditors(
                document!!
            )
            return if (editors.isNotEmpty()) {
                editors[0].project
            } else null
        }

        private fun checkDebug() {
            if (DEBUG_CHECKED) return
            DEBUG_CHECKED = true
            if (!DEBUG) return
            try {
                Messages.showWarningDialog(
                    "Your IDE may respond slower. Disable debug mode from Tools -> IntelliJStatsPlugin Settings.",
                    "IntelliJStatsPlugin Debug Mode Enabled"
                )
            } catch (e: Exception) {
            }
        }

        private fun shouldLogFile(file: VirtualFile?): Boolean {
            if (file == null || file.url.startsWith("mock://")) {
                return false
            }
            val filePath = file.path
            return filePath != "atlassian-ide-plugin.xml" && !filePath.contains("/.idea/workspace.xml")
        }

        private val currentTimestamp: BigDecimal
            get() = BigDecimal((System.currentTimeMillis() / 1000.0).toString()).setScale(4, BigDecimal.ROUND_HALF_UP)

        private fun enoughTimePassed(currentTime: BigDecimal?): Boolean {
            return lastTime.add(FREQUENCY) < currentTime
        }

        private fun getLanguage(file: VirtualFile?): String? {
            val type = file!!.fileType
            return if (type != null) type.name else null
        }

        private fun processHeartbeatQueue(all: Boolean) {
            if (READY) {
                val heartbeats = ArrayList<Heartbeat>()
                var i = 0
                while (true) {
                    val h = heartbeatsQueue.poll() ?: break
                    heartbeats.add(h)
                    i += 1
                    if (i >= 10) break
                }
                sendHeartbeat(heartbeats, all)
                if (all && heartbeatsQueue.size > 0) {
                    processHeartbeatQueue(true)
                }
            }
        }

        private fun sendHeartbeat(heartbeats: ArrayList<Heartbeat>, all: Boolean) {
            try {
                var p = ConfigFile["settings", "apipath"]
                if (p == null) p = ""
                if (p !== "") {
                    if (heartbeats.size > 0) {
                        val json = toJSON(heartbeats)
                        try {
                            val url = URL(p)
                            val con = url.openConnection() as HttpURLConnection
                            con.requestMethod = "POST"
                            con.setRequestProperty("Content-Type", "application/json; utf-8")
                            con.doOutput = true
                            if (DEBUG) {
                                log.debug(json)
                            }
                            con.outputStream.use { os ->
                                val input = json.toByteArray(StandardCharsets.UTF_8)
                                os.write(input, 0, input.size)
                            }
                            BufferedReader(
                                InputStreamReader(con.inputStream, StandardCharsets.UTF_8)
                            ).use { br ->
                                val response = StringBuilder()
                                var responseLine: String? = null
                                while (br.readLine().also { responseLine = it } != null) {
                                    if (DEBUG) {
                                        log.debug(responseLine)
                                    }
                                    response.append(responseLine!!.trim { it <= ' ' })
                                }

                                if (response.toString() == "success") {
                                    if (DEBUG) {
                                        log.debug("Submitted")
                                    }
                                    return
                                } else {
                                    if (DEBUG) {
                                        log.debug("Not submitted")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn(e)
            }
            if (!all) {
                for (heartbeat in heartbeats) {
                    heartbeatsQueue.add(heartbeat)
                }
            }
        }

        private fun toJSON(heartbeats: ArrayList<Heartbeat>): String {
            val json = StringBuffer()
            json.append("[")
            var first = true
            for (heartbeat in heartbeats) {
                val h = StringBuffer()
                h.append("{\"entity\":\"")
                h.append(jsonEscape(heartbeat.entity))
                h.append("\",\"timestamp\":")
                h.append(heartbeat.timestamp!!.toPlainString())
                h.append(",\"is_write\":")
                h.append(heartbeat.isWrite.toString())
                if (heartbeat.project != null) {
                    h.append(",\"project\":\"")
                    h.append(jsonEscape(heartbeat.project))
                    h.append("\"")
                }
                if (heartbeat.language != null) {
                    h.append(",\"language\":\"")
                    h.append(jsonEscape(heartbeat.language))
                    h.append("\"")
                }
                h.append("}")
                if (!first) json.append(",")
                json.append(h)
                first = false
            }
            json.append("]")
            return json.toString()
        }

        private fun jsonEscape(s: String?): String? {
            if (s == null) return null
            val escaped = StringBuffer()
            val len = s.length
            for (i in 0 until len) {
                when (val c = s[i]) {
                    '\\' -> escaped.append("\\\\")
                    '"' -> escaped.append("\\\"")
                    '\b' -> escaped.append("\\b")
                    '\n' -> escaped.append("\\n")
                    '\r' -> escaped.append("\\r")
                    '\t' -> escaped.append("\\t")
                    else -> {
                        val isUnicode =
                            c in '\u0000'..'\u001F' || c in '\u007F'..'\u009F' || c in '\u2000'..'\u20FF'
                        if (isUnicode) {
                            escaped.append("\\u")
                            val hex = Integer.toHexString(c.toInt())
                            var k = 0
                            while (k < 4 - hex.length) {
                                escaped.append('0')
                                k++
                            }
                            escaped.append(hex.toUpperCase())
                        } else {
                            escaped.append(c)
                        }
                    }
                }
            }
            return escaped.toString()
        }
    }
}