package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.LlmCachePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class LlmCacheAdapter(
    private val stringRedisTemplate: StringRedisTemplate
) : LlmCachePort {

    companion object {
        private const val KEY_PREFIX = "llm:deed:"
        private val TTL = Duration.ofDays(7)
    }

    override fun get(sectionHash: String): String? =
        stringRedisTemplate.opsForValue().get("$KEY_PREFIX$sectionHash")

    override fun put(sectionHash: String, result: String) {
        stringRedisTemplate.opsForValue().set("$KEY_PREFIX$sectionHash", result, TTL)
    }
}
