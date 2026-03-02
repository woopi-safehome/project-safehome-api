package com.woopi.safehome.domain._sample.application.usecase

import com.woopi.safehome.domain._sample.adapter.inbound.web.SampleDtoMapper
import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleRequest
import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleResponse
import com.woopi.safehome.domain._sample.application.port.inbound.SampleUseCase
import com.woopi.safehome.domain._sample.application.port.outbound.SamplePersistencePort
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

@Transactional(readOnly = true)
@Service
class SampleUseCaseImpl(
    private val samplePersistencePort: SamplePersistencePort,
) : SampleUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getSampleList(request: SampleRequest.Search): List<SampleResponse> {
        return SampleDtoMapper.toResponse(samplePersistencePort.findAllSample())
    }

    override fun getSampleDetails(id: Long): SampleResponse {
        // 없으면 에러 처리 필요
        val sample = samplePersistencePort.findSampleById(id)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        return SampleDtoMapper.toResponse(sample)
    }

    @Transactional
    override fun createSample(request: SampleRequest.Create): SampleResponse {
        TODO("Not yet implemented")
    }

    @Transactional
    override fun updateSample(request: SampleRequest.Update): SampleResponse {
        TODO("Not yet implemented")
    }

    @Transactional
    override fun deleteSample(id: Long): SampleResponse {
        TODO("Not yet implemented")
    }

    override fun parsePdfSample(file: MultipartFile): String {
        val fileName = file.originalFilename ?: "unknown.pdf"
        val fileSize = file.size
        logger.info("PDF 파싱 시작 - 파일명: {}, 크기: {} bytes", fileName, fileSize)

        return try {
            val result = file.inputStream.use { inputStream ->
                val bytes = inputStream.readBytes()
                Loader.loadPDF(bytes).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.getText(document)
                }
            }
            logger.info("PDF 파싱 완료 - 파일명: {}, 추출 텍스트 길이: {} chars", fileName, result.length)
            logger.debug("PDF 추출 내용:\n{}", result)

            splitSections(result)
            logger.debug("PDF 섹션 분할 완료 - 파일명: {}, 섹션 정보: {}", fileName, splitSections(result).keys)

            result
        } catch (e: IOException) {
            logger.error("PDF 파싱 실패 - 파일명: {}, 오류: {}", fileName, e.message, e)
            throw IllegalArgumentException("유효한 PDF 파일이 아닙니다", e)
        }
    }


    fun splitSections(text: String): Map<String, String> {
        val sectionPattern = Regex("""【\s*([표제부갑구을]+)\s*】""")
        val matches = sectionPattern.findAll(text).toList()

        fun extractSection(keyword: String): String {
            val start = matches.indexOfFirst { it.value.replace(" ", "").contains(keyword) }
            if (start == -1) return ""

            val startPos = matches[start].range.first
            val endPos = if (start + 1 < matches.size) matches[start + 1].range.first else text.length

            return text.substring(startPos, endPos).trim()
        }

        return mapOf(
            "titleSection" to extractSection("표제부"),
            "ownershipSection" to extractSection("갑구"),
            "rightsSection" to extractSection("을구")
        )
    }

}