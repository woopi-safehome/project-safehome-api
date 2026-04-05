package com.woopi.safehome.domain.deed.application.service

import com.woopi.safehome.domain.deed.domain.service.PdfValidationService
import com.woopi.safehome.domain.deed.domain.service.exception.InvalidPdfException
import org.springframework.stereotype.Component

@Component
class DefaultPdfValidationService : PdfValidationService {

    override fun validate(content: ByteArray, contentType: String?) {
        if (content.isEmpty()) {
            throw InvalidPdfException("파일이 비어 있습니다")
        }

        if (contentType != "application/pdf") {
            throw InvalidPdfException("PDF 파일만 업로드할 수 있습니다")
        }

        // 추가 검증들...
    }
}
