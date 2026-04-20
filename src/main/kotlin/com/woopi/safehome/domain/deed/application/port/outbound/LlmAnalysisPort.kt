package com.woopi.safehome.domain.deed.application.port.outbound

import com.woopi.safehome.domain.deed.domain.model.DeedSections

interface LlmAnalysisPort {
    fun analyze(sections: DeedSections, leaseType: String? = null): String
}
