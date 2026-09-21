package com.woopi.safehome.domain.deed.adapter.inbound.web

import com.woopi.safehome.domain.deed.application.port.inbound.DeedUseCase
import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.auth.AnonymousIdArgumentResolver
import com.woopi.safehome.global.auth.CurrentUser
import com.woopi.safehome.global.enums.JobStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.core.MethodParameter
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 요청을 명령으로 옮기는 과정을 고정한다.
 *
 * 이 계층은 형식 변환만 한다. 잘못 옮겨도 컴파일은 되고, 유스케이스 테스트는
 * 명령을 직접 만들어 넣으므로 변환 실수가 드러나지 않는다.
 *
 * 인증은 다른 자리에서 검사하므로 여기서는 고정된 사용자로 대체한다.
 */
class DeedInboundWebAdapterTest : BehaviorSpec({

    val 고정사용자 = object : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentUser::class.java)

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?,
        ) = 77L
    }

    fun fixture(): Pair<DeedUseCase, org.springframework.test.web.servlet.MockMvc> {
        val useCase = mockk<DeedUseCase>()
        val mvc = MockMvcBuilders
            .standaloneSetup(DeedInboundWebAdapter(useCase))
            .setCustomArgumentResolvers(고정사용자, AnonymousIdArgumentResolver())
            .build()
        return useCase to mvc
    }

    Given("업로드 요청") {
        When("파일과 임대차 유형을 보내면") {
            val (useCase, mvc) = fixture()
            val command = slot<DeedCommand.Upload>()
            every { useCase.uploadDeed(capture(command)) } returns "job-9"

            mvc.perform(
                multipart("/api/deed/upload")
                    .file(MockMultipartFile("file", "등기부.pdf", "application/pdf", byteArrayOf(1, 2, 3)))
                    .param("leaseType", "전세")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.jobId").value("job-9"))

            Then("파일 이름과 크기를 명령에 옮긴다") {
                command.captured.fileName shouldBe "등기부.pdf"
                command.captured.fileSize shouldBe 3L
                command.captured.leaseType shouldBe "전세"
            }
            Then("인증에서 얻은 사용자를 명령에 넣는다") {
                // 요청 본문이 아니라 인증에서 온다. 본문에서 받으면 남의 작업을 만들 수 있다.
                command.captured.userId shouldBe 77L
            }
        }

        When("익명 쿠키 없이 올리면") {
            val (useCase, mvc) = fixture()
            val command = slot<DeedCommand.Upload>()
            every { useCase.uploadDeed(capture(command)) } returns "job-11"

            val setCookie = mvc.perform(
                multipart("/api/deed/upload")
                    .file(MockMultipartFile("file", "등기부.pdf", "application/pdf", byteArrayOf(1)))
            ).andExpect(status().isOk)
                .andReturn().response.getHeader("Set-Cookie")

            Then("익명 주인을 새로 만들어 쿠키로 내려주고 같은 값을 명령에 넣는다") {
                // 이 값이 없으면 비회원 결과를 jobId 만으로 누구나 열 수 있다.
                setCookie shouldNotBe null
                val issued = setCookie!!.substringAfter("${AnonymousIdArgumentResolver.COOKIE_NAME}=").substringBefore(';')
                command.captured.anonymousId shouldBe issued
            }

            Then("스크립트가 읽을 수 없는 쿠키다") {
                setCookie!! shouldContain "HttpOnly"
                setCookie shouldContain "SameSite=Lax"
            }
        }

        When("익명 쿠키를 가지고 올리면") {
            val (useCase, mvc) = fixture()
            val command = slot<DeedCommand.Upload>()
            every { useCase.uploadDeed(capture(command)) } returns "job-12"

            val 기존 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            val setCookie = mvc.perform(
                multipart("/api/deed/upload")
                    .file(MockMultipartFile("file", "등기부.pdf", "application/pdf", byteArrayOf(1)))
                    .cookie(jakarta.servlet.http.Cookie(AnonymousIdArgumentResolver.COOKIE_NAME, 기존))
            ).andExpect(status().isOk)
                .andReturn().response.getHeader("Set-Cookie")

            Then("그 값을 그대로 쓰고 새로 발급하지 않는다") {
                command.captured.anonymousId shouldBe 기존
                setCookie shouldBe null
            }
        }

        When("앞단 프록시를 거쳐 들어오면") {
            val (useCase, mvc) = fixture()
            val command = slot<DeedCommand.Upload>()
            every { useCase.uploadDeed(capture(command)) } returns "job-10"

            mvc.perform(
                multipart("/api/deed/upload")
                    .file(MockMultipartFile("file", "등기부.pdf", "application/pdf", byteArrayOf(1)))
                    .with { it.remoteAddr = "10.0.0.5"; it }
                    .header("X-Forwarded-For", "203.0.113.7")
            ).andExpect(status().isOk)

            Then("연결 주소가 아니라 원래 요청자의 주소를 넘긴다") {
                // 연결 주소를 넘기면 웹을 거친 모든 비회원이 한 주소로 묶여 첫 한 건 뒤로 전부 막힌다.
                command.captured.clientAddress shouldBe "203.0.113.7"
            }
        }
    }

    Given("이력 목록 요청") {

        When("쪽수를 지정하지 않으면") {
            val (useCase, mvc) = fixture()
            val pageable = slot<Pageable>()
            every { useCase.getMyJobs(any(), capture(pageable)) } returns PageImpl(emptyList())

            mvc.perform(get("/api/deed/jobs")).andExpect(status().isOk)

            Then("계약에 적힌 기본값을 쓴다") {
                // 요청의 쪽 번호는 0부터다. 응답의 것과 기준이 다르다.
                pageable.captured.pageNumber shouldBe 0
                pageable.captured.pageSize shouldBe 20
            }
        }

        When("쪽수를 지정하면") {
            val (useCase, mvc) = fixture()
            val pageable = slot<Pageable>()
            every { useCase.getMyJobs(any(), capture(pageable)) } returns PageImpl(emptyList())

            mvc.perform(get("/api/deed/jobs").param("page", "2").param("size", "5"))
                .andExpect(status().isOk)

            Then("그대로 전달한다") {
                pageable.captured.pageNumber shouldBe 2
                pageable.captured.pageSize shouldBe 5
            }
        }
    }

    Given("작업 단건 조회") {
        When("식별자를 경로로 주면") {
            val (useCase, mvc) = fixture()
            every { useCase.getJob("job-3", 77L, any()) } returns AnalysisJob.Data(
                id = 1L, jobId = "job-3", fileName = "a.pdf", fileSize = 1L,
                status = JobStatus.COMPLETED, userId = 77L,
            )

            mvc.perform(get("/api/deed/jobs/job-3"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.jobId").value("job-3"))

            Then("경로의 식별자와 인증된 사용자를 함께 넘긴다") {
                io.mockk.verify(exactly = 1) { useCase.getJob("job-3", 77L, any()) }
            }
        }
    }
})
