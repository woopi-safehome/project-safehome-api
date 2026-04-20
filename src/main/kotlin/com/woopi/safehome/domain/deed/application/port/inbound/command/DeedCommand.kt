package com.woopi.safehome.domain.deed.application.port.inbound.command

import org.springframework.web.multipart.MultipartFile

object DeedCommand {

    data class Analyze(
        val file: MultipartFile,
        val fileName: String,
        val fileSize: Long,
        val leaseType: String? = null,
    )
}
