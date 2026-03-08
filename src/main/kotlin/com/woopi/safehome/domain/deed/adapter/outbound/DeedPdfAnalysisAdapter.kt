package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.analysisjob.application.port.outbound.PdfAnalysisException
import com.woopi.safehome.domain.analysisjob.application.port.outbound.PdfAnalysisPort
import com.woopi.safehome.domain.deed.domain.service.PdfParserService
import com.woopi.safehome.domain.deed.domain.service.PdfValidationService
import com.woopi.safehome.domain.deed.domain.service.exception.InvalidPdfException
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class DeedPdfAnalysisAdapter(
    private val pdfValidationService: PdfValidationService,
    private val pdfParserService: PdfParserService
) : PdfAnalysisPort {

    override fun process(file: MultipartFile) {
        try {
            pdfValidationService.validate(file)
            pdfParserService.parse(file)
        } catch (e: InvalidPdfException) {
            throw PdfAnalysisException(e.message ?: "PDF 검증 실패")
        }
    }

}
