package com.woopi.safehome.domain.analysisjob.application.port.outbound

import org.springframework.web.multipart.MultipartFile

/**
 * PDF 분석 처리 포트 (analysisjob → deed 의존 제거를 위한 추상화)
 * 구현체: deed/adapter/outbound/DeedPdfAnalysisAdapter
 */
interface PdfAnalysisPort {
    /**
     * PDF 유효성 검증 + 파싱 수행
     * @throws PdfAnalysisException 유효하지 않은 PDF인 경우
     */
    fun process(file: MultipartFile): PdfContent
}

/** analysisjob이 deed를 직접 알지 않도록 analysisjob 포트 패키지에 정의 */
data class PdfContent(
    val sections: Map<String, List<String>>
)

class PdfAnalysisException(message: String) : RuntimeException(message)
