package com.woopi.safehome.domain.deed.domain.service.impl

import com.woopi.safehome.domain.deed.domain.service.PdfValidationService
import com.woopi.safehome.domain.deed.domain.service.exception.InvalidPdfException
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class DefaultPdfValidationService : PdfValidationService {

    override fun validate(file: MultipartFile) {
        if (file.isEmpty) {
            throw InvalidPdfException("파일이 비어 있습니다")
        }

        if (file.contentType != "application/pdf") {
            throw InvalidPdfException("PDF 파일만 업로드할 수 있습니다")
        }

        // 추가 검증들...
    }
}