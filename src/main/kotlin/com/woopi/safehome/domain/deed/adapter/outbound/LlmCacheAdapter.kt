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
        private const val KEY_PREFIX = "llm:deed:v2:"
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
