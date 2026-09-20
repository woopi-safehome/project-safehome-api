package com.woopi.safehome.global.web

import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress

/**
 * 요청을 보낸 쪽의 주소. 비회원 사용량을 세는 단서로만 쓴다.
 *
 * 앞에 프록시(웹 프론트·리버스 프록시)를 두면 연결 상대는 그 프록시라, 그대로 쓰면
 * **모든 비회원이 한 주소로 보여 첫 한 건 뒤로 전부 막힌다.** 그래서 전달 헤더를 본다.
 *
 * **다만 헤더는 누구나 붙일 수 있다.** 그래서 **연결 상대가 사설망일 때만** 믿는다 —
 * 그 경우에만 앞단이 우리 프록시다. 인터넷에서 직접 온 요청이 헤더를 붙여도 무시한다.
 * 프록시가 사설망 밖에 있으면 이 판단이 틀리므로, 그때는 배치를 다시 봐야 한다.
 *
 * 이 값은 인증이나 권한에 쓰지 않는다. 우회되어도 비회원 전체 천장이 비용을 막는다.
 */
object ClientAddress {

    private const val FORWARDED_FOR = "X-Forwarded-For"

    fun of(request: HttpServletRequest): String? {
        val peer: String? = request.remoteAddr

        if (peer != null && isInternal(peer)) {
            // 여러 프록시를 거치면 값이 쉼표로 이어진다. 맨 앞이 원래 요청자다.
            request.getHeader(FORWARDED_FOR)
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        return peer
    }

    /** 우리 앞단으로 볼 수 있는 주소인가 — 루프백과 사설망. IPv6 유니크 로컬(fc00::/7)도 사설로 본다. */
    private fun isInternal(address: String): Boolean = runCatching {
        val parsed = InetAddress.getByName(address)
        parsed.isLoopbackAddress ||
            parsed.isSiteLocalAddress ||
            parsed.isLinkLocalAddress ||
            parsed.isAnyLocalAddress ||
            (parsed.address.size == 16 && (parsed.address[0].toInt() and 0xFE) == 0xFC)
    }.getOrDefault(false)
}
