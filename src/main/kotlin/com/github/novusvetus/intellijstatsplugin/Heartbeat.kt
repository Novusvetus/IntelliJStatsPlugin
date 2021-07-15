package com.github.novusvetus.intellijstatsplugin

import java.math.BigDecimal

class Heartbeat {
    var entity: String? = null
    var timestamp: BigDecimal? = null
    var isWrite: Boolean? = null
    var project: String? = null
    var language: String? = null
}