package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.LlmCachePort
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class LlmCacheAdapter(
    private val stringRedisTemplate: StringRedisTemplate
) : LlmCachePort {

    private val log = LoggerFactory.getLogger(LlmCacheAdapter::class.java)

    companion object {
        // v3: 분석 서버가 말소된 권리를 빼고 건수·합계를 코드로 세게 바뀌었다. 올리지 않으면 옛 결과가 만료까지 나간다.
        // v4: 판정 문구·권고·지식 데이터의 사실관계를 법령 원문으로 고쳤다(2026-09-27). 옛 결과에 틀린 안내가 남아 있다.
        // v5: 서술 호출에 등급 코드 대신 한국어 등급을 준다. 옛 결과 요약에 "DANGER 수준" 같은 코드가 남아 있다.
        // v6: 지식 데이터 사례 4건·경험 기준 수치 수정, 권고 문구의 70~80%·80% 에 근거 표기.
        private const val KEY_PREFIX = "llm:deed:v6:"
        private val TTL = Duration.ofDays(7)
    }

    override fun get(sectionHash: String): String? = try {
        stringRedisTemplate.opsForValue().get("$KEY_PREFIX$sectionHash")
    } catch (e: Exception) {
        log.warn("[LLM_CACHE] Redis 연결 실패, 캐시 조회 건너뜀. hash={}", sectionHash, e)
        null
    }

    override fun put(sectionHash: String, result: String) {
        try {
            stringRedisTemplate.opsForValue().set("$KEY_PREFIX$sectionHash", result, TTL)
        } catch (e: Exception) {
            log.warn("[LLM_CACHE] Redis 연결 실패, 캐시 저장 건너뜀. hash={}", sectionHash, e)
        }
    }
}
