package com.woopi.safehome.domain.deed.application.usecase

import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 등기부 분석 유스케이스의 불변식을 포트 대역으로 검증한다.
 *
 * 배경: domain/deed/README.md 의 "불변식"
 */
class DeedUseCaseImplTest : BehaviorSpec({

    fun job(
        jobId: String = "job-1",
        userId: Long? = 1L,
        status: JobStatus = JobStatus.PENDING,
    ) = AnalysisJob.Data(
        id = 1L,
        jobId = jobId,
        fileName = "deed.pdf",
        fileSize = 100L,
        status = status,
        userId = userId,
        step = if (status == JobStatus.COMPLETED) AnalysisStep.POST_PROCESSING else null,
    )

    Given("업로드") {

        When("작업을 맡기면") {
            val jobs = mockk<JobPersistencePort>()
            val sse = mockk<SseNotifierPort>()
            val executor = mockk<AnalysisExecutorPort>()
            val useCase = DeedUseCaseImpl(jobs, sse, executor)

            every { jobs.create(any()) } returns job()
            every { executor.execute(any(), any(), any(), any(), any()) } just Runs

            val command = DeedCommand.Upload(
                file = MockMultipartFile("file", "deed.pdf", "application/pdf", byteArrayOf(1, 2)),
                fileName = "deed.pdf",
                fileSize = 2L,
                userId = 1L,
                leaseType = "전세",
            )

            // 트랜잭션 동기화를 열어 두고 커밋 시점을 직접 흉내낸다
            TransactionSynchronizationManager.initSynchronization()
            val jobId: String
            val startedBeforeCommit: Boolean
            try {
                jobId = useCase.uploadDeed(command)
                startedBeforeCommit =
                    runCatching { verify(exactly = 1) { executor.execute(any(), any(), any(), any(), any()) } }
                        .isSuccess
                TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }

            Then("작업을 만들고 식별자를 돌려준다") {
                // 식별자는 유스케이스가 만든다. 저장소가 돌려준 값이 아니다.
                jobId.shouldNotBeBlank()
                verify(exactly = 1) { jobs.create(match { it.jobId == jobId }) }
            }

            Then("분석은 커밋 전에 시작되지 않는다") {
                // 먼저 띄우면 그 스레드가 아직 존재하지 않는 작업을 조회한다.
                // 타이밍에 따라 되기도 해서 재현이 어렵다.
                startedBeforeCommit shouldBe false
            }

            Then("커밋 뒤에 분석이 시작된다") {
                verify(exactly = 1) { executor.execute(jobId, any(), any(), "전세", 1L) }
            }
        }
    }

    Given("진행 상황 구독") {

        When("남의 작업을 구독하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk())
            every { jobs.findByJobId("job-1") } returns job(userId = 99L)

            Then("접근을 막는다") {
                // 식별자를 알아도 접근할 수 없어야 한다
                shouldThrow<BusinessException> { useCase.streamJob("job-1", userId = 1L) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("구독 시점에 분석이 이미 끝나 있으면") {
            val jobs = mockk<JobPersistencePort>()
            val sse = mockk<SseNotifierPort>()
            val useCase = DeedUseCaseImpl(jobs, sse, mockk())

            every { jobs.findByJobId("job-1") } returns job(status = JobStatus.COMPLETED)
            every { sse.createEmitter("job-1") } returns SseEmitter()
            every { sse.notifyStep(any(), any(), any(), any()) } just Runs

            useCase.streamJob("job-1", userId = 1L)

            Then("최종 상태를 즉시 보낸다") {
                // 이 처리가 없으면 빠르게 끝난 분석에서 클라이언트가 영원히 기다린다
                verify(exactly = 1) {
                    sse.notifyStep("job-1", JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, any())
                }
            }
        }
    }

    Given("작업 조회") {

        When("남의 작업을 조회하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk())
            every { jobs.findByJobId("job-1") } returns job(userId = 99L)

            Then("접근을 막는다") {
                shouldThrow<BusinessException> { useCase.getJob("job-1", userId = 1L) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("없는 작업을 조회하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk())
            every { jobs.findByJobId("nope") } returns null

            Then("찾을 수 없다고 알린다") {
                shouldThrow<BusinessException> { useCase.getJob("nope", userId = 1L) }
                    .errorCode shouldBe ErrorCode.NOT_FOUND
            }
        }
    }
})
