package com.woopi.safehome.domain.deed.application.port.outbound

interface LlmCachePort {
    fun get(sectionHash: String): String?
    fun put(sectionHash: String, result: String)
}
