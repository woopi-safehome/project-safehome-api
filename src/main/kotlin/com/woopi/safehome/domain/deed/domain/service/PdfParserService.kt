package com.woopi.safehome.domain.deed.domain.service

import com.woopi.safehome.domain.deed.domain.model.DeedSections

interface PdfParserService {
    fun parse(content: ByteArray): DeedSections
}
