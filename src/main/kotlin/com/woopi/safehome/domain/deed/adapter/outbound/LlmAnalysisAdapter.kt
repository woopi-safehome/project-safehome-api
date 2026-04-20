package com.woopi.safehome.domain.deed.adapter.outbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.woopi.safehome.domain.deed.application.port.outbound.LlmAnalysisPort
import com.woopi.safehome.domain.deed.domain.model.DeedSections
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class LlmAnalysisAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${safehome.ai-api.url}") aiApiUrl: String,
) : LlmAnalysisPort {

    private val log = LoggerFactory.getLogger(LlmAnalysisAdapter::class.java)
    private val restClient = RestClient.builder().baseUrl(aiApiUrl).build()

    override fun analyze(sections: DeedSections, leaseType: String?): String {
        val totalLines = sections.sections.values.sumOf { it.size }
        log.info("[LLM_ANALYSIS] 요청. sections={}, totalLines={}, leaseType={}", sections.sectionNames(), totalLines, leaseType)

        val requestBody = buildMap {
            put("sections", sections.sections)
            if (leaseType != null) put("leaseType", leaseType)
        }

        @Suppress("UNCHECKED_CAST")
        val response = restClient.post()
            .uri("/api/deed/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, res ->
                val errorBody = res.body.bufferedReader().readText()
                log.error("[LLM_ANALYSIS] AI API 오류. status={}, body={}", res.statusCode, errorBody)
                throw RestClientResponseException(
                    "AI API 오류: ${res.statusCode}",
                    res.statusCode.value(), res.statusCode.toString(), null, null, null
                )
            }
            .body(Map::class.java) as Map<String, Any>

        val analysis = response["analysis"]
            ?: throw IllegalStateException("AI API 응답에 'analysis' 필드가 없습니다")

        val result = objectMapper.writeValueAsString(analysis)

        @Suppress("UNCHECKED_CAST")
        val analysisMap = analysis as? Map<String, Any>
        log.info(
            "[LLM_ANALYSIS] 응답. safetyLevel={}, summary={}",
            analysisMap?.get("safetyLevel"),
            (analysisMap?.get("summary") as? String)?.take(80),
        )

        return result
    }
}
