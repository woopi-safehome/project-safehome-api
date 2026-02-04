package com.woopi.safehome.domain._sample.application.port.inbound

import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleRequest
import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleResponse

interface SampleUseCase {

    /**
     * 샘플 리스트 조회
     */
    fun getSampleList(request: SampleRequest.Search): List<SampleResponse>

    /**
     * 샘플 상세 조회
     */
    fun getSampleDetails(id: Long): SampleResponse

    /**
     * 샘플 생성
     */
    fun createSample(request: SampleRequest.Create): SampleResponse

    /**
     * 샘플 수정
     */
    fun updateSample(request: SampleRequest.Update): SampleResponse

    /**
     * 샘플 삭제
     */
    fun deleteSample(id: Long): SampleResponse

}