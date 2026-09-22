package com.woopi.safehome.global.exception.handler

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * 업로드 한도를 넘긴 요청이 사용자에게 어떻게 보이는지 고정한다.
 *
 * 이 예외는 **컨트롤러에 닿기 전에** 멀티파트를 푸는 단계에서 터진다. 그래서 업로드
 * 엔드포인트의 코드를 아무리 봐도 이 경우가 보이지 않고, 따로 받지 않으면 포괄 핸들러로
 * 떨어져 **"서버 내부 오류"** 가 나간다. 사용자가 할 수 있는 일(더 작은 파일)이 있는데도
 * 서버 잘못처럼 보이고, 5xx 로 분류되어 Sentry 에도 쌓인다.
 *
 * 한도를 올리는 것만으로는 이 문제가 사라지지 않는다 — 한도가 얼마든 넘기는 파일은 있다.
 *
 * 배경: README.md 의 "App→API 계약" 절, 에러 코드 표
 */
class GlobalExceptionHandlerTest : BehaviorSpec({

    val 한도바이트 = 10L * 1024 * 1024

    @RestController
    class 한도를넘긴업로드 {
        @PostMapping("/test/upload")
        fun upload(): Nothing = throw MaxUploadSizeExceededException(한도바이트)
    }

    val mvc = MockMvcBuilders
        .standaloneSetup(한도를넘긴업로드())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    Given("업로드 한도를 넘긴 요청") {

        When("멀티파트를 푸는 단계에서 거절되면") {

            Then("413 과 FILE_TOO_LARGE 로 응답한다") {
                mvc.perform(post("/test/upload").contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isPayloadTooLarge)
                    .andExpect(jsonPath("$.type").value("error"))
                    // INTERNAL_SERVER_ERROR 가 나오면 포괄 핸들러로 떨어진 것이다
                    .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
            }

            Then("허용 크기를 함께 알려 준다") {
                // 한도는 설정이 원본이다. 메시지나 문서에 복제하지 않고 예외가 들고 온 값을 그대로 싣는다.
                mvc.perform(post("/test/upload").contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(jsonPath("$.details.maxBytes").value(한도바이트))
            }
        }
    }
})
