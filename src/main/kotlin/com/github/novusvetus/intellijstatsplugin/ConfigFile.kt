package com.github.novusvetus.intellijstatsplugin

import java.io.*

object ConfigFile {
    private const val fileName = ".intellijstatsplugin.cfg"
    private var cachedConfigFile: String? = null
    operator fun get(section: String, key: String): String? {
        val file = configFilePath
        var `val`: String? = null
        try {
            val br = BufferedReader(FileReader(file))
            var currentSection = ""
            try {
                var line = br.readLine()
                while (line != null) {
                    if (line.trim { it <= ' ' }.startsWith("[") && line.trim { it <= ' ' }.endsWith("]")) {
                        currentSection =
                            line.trim { it <= ' ' }.substring(1, line.trim { it <= ' ' }.length - 1).toLowerCase()
                    } else {
                        if (section.toLowerCase() == currentSection) {
                            val parts = line.split("=".toRegex()).toTypedArray()
                            if (parts.size >= 2 && parts[0].trim { it <= ' ' } == key) {
                                val x = StringBuffer()
                                var first = true
                                for (i in 1 until parts.size) {
                                    if (first) {
                                        first = false
                                    } else {
                                        x.append("=")
                                    }
                                    x.append(parts[i])
                                }
                                `val` = x.toString().trim { it <= ' ' }
                                br.close()
                                return `val`
                            }
                        }
                    }
                    line = br.readLine()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    br.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e1: FileNotFoundException) { /* ignored */
        }
        return `val`
    }

    private val configFilePath: String?
        private get() {
            if (cachedConfigFile == null) {
                if (System.getenv("INTELLIJSTATSPLUGIN_HOME") != null && System.getenv("INTELLIJSTATSPLUGIN_HOME")
                        .trim { it <= ' ' }.isNotEmpty()
                ) {
                    val folder = File(System.getenv("INTELLIJSTATSPLUGIN_HOME"))
                    if (folder.exists()) {
                        cachedConfigFile = File(folder, fileName).absolutePath
                        IntelliJStatsPlugin.log.debug("Using \$INTELLIJSTATSPLUGIN_HOME for config folder: $cachedConfigFile")
                        return cachedConfigFile
                    }
                }
                cachedConfigFile = File(System.getProperty("user.home"), fileName).absolutePath
                IntelliJStatsPlugin.log.debug("Using \$HOME for config folder: $cachedConfigFile")
            }
            return cachedConfigFile
        }

    operator fun set(section: String, key: String, `val`: String) {
        val file = configFilePath
        var contents = StringBuilder()
        try {
            val br = BufferedReader(FileReader(file))
            try {
                var currentSection = ""
                var line = br.readLine()
                var found = false
                while (line != null) {
                    if (line.trim { it <= ' ' }.startsWith("[") && line.trim { it <= ' ' }.endsWith("]")) {
                        if (section.toLowerCase() == currentSection && !found) {
                            contents.append("$key = $`val`\n")
                            found = true
                        }
                        currentSection =
                            line.trim { it <= ' ' }.substring(1, line.trim { it <= ' ' }.length - 1).toLowerCase()
                        contents.append(
                            """
    $line
    
    """.trimIndent()
                        )
                    } else {
                        if (section.toLowerCase() == currentSection) {
                            val parts = line.split("=".toRegex()).toTypedArray()
                            val currentKey = parts[0].trim { it <= ' ' }
                            if (currentKey == key) {
                                if (!found) {
                                    contents.append("$key = $`val`\n")
                                    found = true
                                }
                            } else {
                                contents.append(
                                    """
    $line
    
    """.trimIndent()
                                )
                            }
                        } else {
                            contents.append(
                                """
    $line
    
    """.trimIndent()
                            )
                        }
                    }
                    line = br.readLine()
                }
                if (!found) {
                    if (section.toLowerCase() != currentSection) {
                        contents.append(
                            """
    [${section.toLowerCase()}]
    
    """.trimIndent()
                        )
                    }
                    contents.append("$key = $`val`\n")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    br.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e1: FileNotFoundException) {

            // cannot read config file, so create it
            contents = StringBuilder()
            contents.append(
                """
    [${section.toLowerCase()}]
    
    """.trimIndent()
            )
            contents.append("$key = $`val`\n")
        }
        var writer: PrintWriter? = null
        try {
            writer = PrintWriter(file, "UTF-8")
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        if (writer != null) {
            writer.print(contents)
            writer.close()
        }
    }
}