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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

@Transactional(readOnly = true)
@Service
class SampleUseCaseImpl(
    private val samplePersistencePort: SamplePersistencePort,
) : SampleUseCase {

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

    override fun parsePdfSample(file:MultipartFile): String {
        return try {
            file.inputStream.use { inputStream ->
                val bytes = inputStream.readBytes()
                Loader.loadPDF(bytes).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.getText(document)
                }
            }
        } catch (e: IOException) {
            throw IllegalArgumentException("유효한 PDF 파일이 아닙니다", e)
        }
    }

}