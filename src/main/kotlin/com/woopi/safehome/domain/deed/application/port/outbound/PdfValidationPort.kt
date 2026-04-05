package com.woopi.safehome.domain.deed.application.port.outbound

interface PdfValidationPort {
    fun validate(content: ByteArray, contentType: String?)
}
