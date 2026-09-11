package com.woopi.safehome.global.config

import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * 나가는 HTTP 호출 클라이언트를 만드는 유일한 지점.
 *
 * 클라이언트를 각자 만들면 타임아웃을 빠뜨려도 아무 표시가 나지 않는다.
 * 응답이 오지 않는 상류에 요청이 매달리고, 그 스레드는 돌아오지 않는다.
 * 그래서 타임아웃을 인자로 강제하고, 다른 곳에서 직접 만드는 것을 제약
 * 테스트가 막는다.
 *
 * 값은 호출하는 쪽이 정한다. 무엇이 느려도 되는 호출인지는 그쪽만 안다.
 *
 * 주입받아 쓴다. 어댑터가 직접 만들면 테스트에서 바깥으로 나가는 길을 막을 수 없다.
 */
@Component
class OutboundHttp {

    fun restClient(
        connectTimeout: Duration,
        readTimeout: Duration,
        baseUrl: String? = null,
    ): RestClient {
        val builder = RestClient.builder().requestFactory(factory(connectTimeout, readTimeout))
        if (baseUrl != null) builder.baseUrl(baseUrl)
        return builder.build()
    }

    fun restTemplate(
        connectTimeout: Duration,
        readTimeout: Duration,
    ): RestTemplate = RestTemplate(factory(connectTimeout, readTimeout))

    private fun factory(connect: Duration, read: Duration): ClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connect)
            setReadTimeout(read)
        }
}
