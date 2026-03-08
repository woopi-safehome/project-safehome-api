package com.woopi.safehome.domain.deed.adapter.inbound.web.dto

import com.woopi.safehome.domain.analysisjob.model.AnalysisJob

object DeedDtoMapper {

    fun toResponse(model: AnalysisJob.Data): DeedResponse {
        return DeedResponse(
            id = 0L,
            jobId = model.jobId,
            description = model.description,
        )
    }

}