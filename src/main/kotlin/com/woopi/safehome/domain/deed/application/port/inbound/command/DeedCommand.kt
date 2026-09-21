package com.woopi.safehome.domain.deed.application.port.inbound.command

import org.springframework.web.multipart.MultipartFile

object DeedCommand {

    data class Upload(
        val file: MultipartFile,
        val fileName: String,
        val fileSize: Long,
        val userId: Long? = null,
        val leaseType: String? = null,
        /** 비회원의 하루 사용량을 세는 단서. 회원이면 쓰지 않는다. */
        val clientAddress: String? = null,
        /** 비회원 작업의 주인. 쿠키에서 오며, 회원이면 쓰지 않는다. */
        val anonymousId: String? = null,
    )
}
