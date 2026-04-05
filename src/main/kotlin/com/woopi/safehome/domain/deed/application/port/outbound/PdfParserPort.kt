package com.woopi.safehome.domain.deed.application.port.outbound

import com.woopi.safehome.domain.deed.domain.model.DeedSections

interface PdfParserPort {
    fun parse(content: ByteArray): DeedSections
}
