package com.woopi.safehome.global.exception.handler

import io.kotest.core.spec.style.BehaviorSpec
import org.hamcrest.Matchers.nullValue
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

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

    @RestController
    class 요청단계오류 {
        // 정적 자원 처리기가 없는 단독 구성이라, 없는 경로를 운영과 같은 예외로 흉내 낸다.
        @GetMapping("/test/missing")
        fun missing(): Nothing = throw NoResourceFoundException(HttpMethod.GET, "api/missing")

        @PostMapping("/test/json")
        fun json(@RequestBody body: Map<String, Any>): Map<String, Any> = body

        @GetMapping("/test/number")
        fun number(@RequestParam n: Int): Int = n

        @GetMapping("/test/boom")
        fun boom(): Nothing = throw IllegalStateException("SELECT * FROM secret_table")
    }

    val mvc = MockMvcBuilders
        .standaloneSetup(한도를넘긴업로드(), 요청단계오류())
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

    /*
     * 요청한 쪽의 잘못이 500 으로 나가지 않는지 고정한다. 이 예외들도 컨트롤러에 닿기 전에 터져
     * 따로 받지 않으면 포괄 핸들러로 떨어진다. API 가 인터넷에 열려 있어 경로를 훑는 요청이
     * 끊임없이 오고, 500 이면 그것이 전부 서버 오류로 Sentry 에 쌓인다.
     *
     * 배경: README.md 의 "App→API 계약" 절, 에러 코드 표 · global/README.md 의 "프레임워크가 던지는 요청 오류는 포괄 핸들러로 떨어진다"
     */
    Given("요청 단계에서 거절된 요청") {

        // INTERNAL_SERVER_ERROR 가 나오면 포괄 핸들러로 떨어진 것이다
        Then("없는 경로는 404 와 NOT_FOUND") {
            mvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        }

        Then("맞지 않는 메서드는 405 와 METHOD_NOT_ALLOWED") {
            mvc.perform(post("/test/missing"))
                .andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        }

        Then("맞지 않는 본문 형식은 415 와 UNSUPPORTED_MEDIA_TYPE") {
            mvc.perform(post("/test/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        }

        Then("읽을 수 없는 본문은 400 과 VALIDATION_FAILED") {
            mvc.perform(post("/test/json").contentType(MediaType.APPLICATION_JSON).content("{깨진"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }

        Then("빠진 인자와 타입이 맞지 않는 인자는 400 과 VALIDATION_FAILED") {
            mvc.perform(get("/test/number"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            mvc.perform(get("/test/number").param("n", "숫자아님"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }
    }

    Given("서버 쪽 오류") {

        Then("500 으로 응답하되 예외 메시지를 밖으로 내보내지 않는다") {
            // 메시지에는 쿼리·경로·클래스 이름 같은 내부 구현이 담긴다. 원인은 Sentry 가 갖는다.
            mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.details").value(nullValue()))
        }
    }
})
