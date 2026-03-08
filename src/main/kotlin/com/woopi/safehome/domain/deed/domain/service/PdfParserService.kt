package com.woopi.safehome.domain.deed.domain.service

import com.woopi.safehome.domain.deed.domain.model.DeedSections
import org.springframework.web.multipart.MultipartFile

interface PdfParserService {
    fun parse(file: MultipartFile): DeedSections
}
