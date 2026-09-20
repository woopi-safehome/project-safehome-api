package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.AnonymousUsagePort
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate

/**
 * 비회원 사용량을 하루치 키에 센다.
 *
 * **주소를 그대로 저장하지 않는다.** IP 는 개인정보로 다뤄야 하므로 해시만 남기고,
 * 날짜가 바뀌면 키 자체가 바뀌며 보관 기간이 지나면 사라진다.
 *
 * 키에 날짜를 넣는 것으로 하루 경계를 만든다. 자정이 지나면 새 키를 쓰므로
 * 따로 비우는 일이 필요 없다.
 */
@Component
class AnonymousUsageRedisAdapter(
    private val stringRedisTemplate: StringRedisTemplate,
) : AnonymousUsagePort {

    private val log = LoggerFactory.getLogger(AnonymousUsageRedisAdapter::class.java)

    companion object {
        private const val KEY_PREFIX = "quota:anon:v1:"
        /** 하루 경계만 넘기면 되지만, 시간대 차이와 지연을 감안해 넉넉히 둔다. */
        private val TTL = Duration.ofDays(2)
    }

    override fun increaseClientUsage(clientAddress: String): Long? =
        increase("$KEY_PREFIX${today()}:client:${hash(clientAddress)}")

    override fun increaseTotalUsage(): Long? =
        increase("$KEY_PREFIX${today()}:total")

    private fun today(): String = LocalDate.now().toString()

    /** IP 를 그대로 남기지 않기 위한 것이다. 되돌릴 필요가 없으므로 단방향으로 충분하다. */
    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    /**
     * 늘리고 늘린 값을 돌려준다. 처음 늘릴 때만 만료를 건다 —
     * 매번 걸면 계속 쓰는 주소의 키가 영원히 살아남는다.
     */
    private fun increase(key: String): Long? = try {
        val count = stringRedisTemplate.opsForValue().increment(key)
        if (count == 1L) stringRedisTemplate.expire(key, TTL)
        count
    } catch (e: Exception) {
        // 셀 수 없다는 사실만 알린다. 막을지 통과시킬지는 유스케이스가 정한다.
        log.warn("[ANON_QUOTA] Redis 연결 실패, 사용량을 세지 못했다. key={}", key, e)
        null
    }
}
