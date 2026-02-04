package com.woopi.safehome.domain.deed.domain.service

import org.springframework.web.multipart.MultipartFile

interface PdfValidationService {
    fun validate(file: MultipartFile)
}