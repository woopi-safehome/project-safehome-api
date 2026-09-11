package com.woopi.safehome.domain.deed.adapter.outbound.persistence

import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import java.util.UUID

/**
 * 분석 작업의 저장 방식을 고정한다.
 *
 * 완료 시 결과에서 요약 정보를 뽑아 별도 컬럼에 넣는다. 이력 목록에서 결과
 * 전문을 파싱하지 않기 위해서다. 유스케이스 테스트는 이 어댑터를 대역으로
 * 바꾸므로 여기가 망가져도 거기서는 드러나지 않는다.
 *
 * 배경: domain/deed/README.md 의 "설계 결정"
 */
@SpringBootTest
class JobPersistenceAdapterTest(
    @Autowired private val jobs: JobPersistencePort,
) : BehaviorSpec({

    fun newJob(userId: Long) = jobs.create(
        AnalysisJob.Create(
            jobId = UUID.randomUUID().toString(),
            fileName = "deed.pdf",
            fileSize = 1_000L,
            status = JobStatus.PENDING,
            userId = userId,
            leaseType = "전세",
        )
    )

    Given("새로 만든 작업") {
        val created = newJob(userId = 8_810_001L)

        When("식별자로 찾으면") {
            Then("맡긴 값 그대로 나온다") {
                val found = jobs.findByJobId(created.jobId)
                found?.fileName shouldBe "deed.pdf"
                found?.status shouldBe JobStatus.PENDING
                found?.userId shouldBe 8_810_001L
                found?.leaseType shouldBe "전세"
            }
        }

        When("없는 식별자로 찾으면") {
            Then("빈 값이 나온다") {
                jobs.findByJobId("없는-식별자").shouldBeNull()
            }
        }
    }

    Given("진행 중인 작업") {
        val created = newJob(userId = 8_810_002L)

        When("단계를 갱신하면") {
            jobs.updateStatus(created.jobId, JobStatus.IN_PROGRESS, AnalysisStep.LLM_ANALYSIS, "분석 중")

            Then("상태와 단계와 사유가 함께 남는다") {
                val found = jobs.findByJobId(created.jobId)
                found?.status shouldBe JobStatus.IN_PROGRESS
                found?.step shouldBe AnalysisStep.LLM_ANALYSIS
                found?.description shouldBe "분석 중"
            }
        }
    }

    Given("분석이 끝난 작업") {
        val created = newJob(userId = 8_810_003L)

        When("완료로 기록하면") {
            jobs.complete(created.jobId, """{"safetyLevel":"DANGER"}""", SafetyLevel.DANGER, "서울시 마포구")

            Then("결과 전문과 요약 정보가 따로 남는다") {
                // 이력 목록이 결과 전문을 파싱하지 않도록 요약을 별도 컬럼에 둔다
                val found = jobs.findByJobId(created.jobId)
                found?.status shouldBe JobStatus.COMPLETED
                found?.result shouldBe """{"safetyLevel":"DANGER"}"""
                found?.safetyLevel shouldBe SafetyLevel.DANGER
                found?.address shouldBe "서울시 마포구"
            }
        }
    }

    Given("여러 사용자의 작업") {
        val mine = newJob(userId = 8_810_004L)
        newJob(userId = 8_810_005L)

        When("내 이력을 조회하면") {
            val page = jobs.findByUserId(8_810_004L, PageRequest.of(0, 20))

            Then("남의 작업은 섞이지 않는다") {
                page.content.map { it.jobId } shouldBe listOf(mine.jobId)
            }
        }
    }
})
