package com.woopi.safehome.domain.deed.domain.service

interface PdfValidationService {
    fun validate(content: ByteArray, contentType: String?)
}