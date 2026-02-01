package com.woopi.safehome.domain.analysisjob.application.port.inbound

interface JobUseCase {

    /**
     * 웹소켓 샘플 - Job ID 생성 후 조회
     */
    fun createJobId(): String

}