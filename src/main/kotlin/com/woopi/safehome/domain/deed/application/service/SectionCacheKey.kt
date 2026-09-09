package com.woopi.safehome.domain.deed.application.service

import com.woopi.safehome.domain.deed.domain.model.DeedSections
import java.security.MessageDigest

/**
 * 분석 캐시 키의 본문을 만든다. 저장 시 앞에 버전 접두사가 붙는다.
 *
 * 본문을 그대로 해싱하지 않고 정규화한 뒤 해싱한다. 문서에서 텍스트를 뽑는
 * 라이브러리가 운영체제와 버전에 따라 공백과 줄바꿈을 다르게 내기 때문에,
 * 정규화하지 않으면 내용이 같은데 해시가 달라져 캐시가 사실상 동작하지 않는다.
 *
 * 정규화는 키 계산에만 쓴다. 분석 서버로 보내는 본문은 원본 그대로다.
 * 이 둘을 섞으면 캐시된 결과와 새 결과가 서로 다른 입력에서 나온 것이 된다.
 *
 * 배경: docs/llm-cache-strategy.md
 */
internal object SectionCacheKey {

    private const val UNSPECIFIED_LEASE_TYPE = "미지정"

    /**
     * 임대차 유형을 해시에 넣지 않고 따로 붙인다.
     * 같은 등기부라도 유형에 따라 분석 결과가 달라지므로 독립적으로 캐싱해야 한다.
     */
    fun of(sections: DeedSections, leaseType: String?): String =
        "${hash(sections)}:${leaseType ?: UNSPECIFIED_LEASE_TYPE}"

    private fun hash(sections: DeedSections): String {
        val content = sections.sections.entries
            .sortedBy { it.key }
            .joinToString("|") { (name, lines) ->
                "$name:${lines.joinToString("\n") { normalize(it) }}"
            }
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun normalize(text: String): String =
        text.replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
