package com.woopi.safehome.domain.deed.application.usecase

import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.application.port.outbound.AnonymousUsagePort
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
import java.time.LocalDate

/**
 * 등기부 분석 유스케이스의 불변식을 포트 대역으로 검증한다.
 *
 * 배경: domain/deed/README.md 의 "불변식"
 */
class DeedUseCaseImplTest : BehaviorSpec({

    // 익명 쿠키가 가리키는 브라우저. 값이 다르면 다른 브라우저다.
    val 브라우저A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    val 브라우저B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

    fun job(
        jobId: String = "job-1",
        userId: Long? = 1L,
        anonymousId: String? = null,
        status: JobStatus = JobStatus.PENDING,
    ) = AnalysisJob.Data(
        id = 1L,
        jobId = jobId,
        fileName = "deed.pdf",
        fileSize = 100L,
        status = status,
        userId = userId,
        anonymousId = anonymousId,
        step = if (status == JobStatus.COMPLETED) AnalysisStep.POST_PROCESSING else null,
    )

    fun anonymousUpload(clientAddress: String?, anonymousId: String = 브라우저A) = DeedCommand.Upload(
        file = MockMultipartFile("file", "deed.pdf", "application/pdf", byteArrayOf(1, 2)),
        fileName = "deed.pdf",
        fileSize = 2L,
        userId = null,
        clientAddress = clientAddress,
        anonymousId = anonymousId,
    )

    Given("업로드") {

        When("작업을 맡기면") {
            val jobs = mockk<JobPersistencePort>()
            val sse = mockk<SseNotifierPort>()
            val executor = mockk<AnalysisExecutorPort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, sse, executor, anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)

            every { jobs.countStartedSince(1L, any()) } returns 0L
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

            Then("회원 작업에는 익명 주인을 남기지 않는다") {
                // 주인이 둘이면 어느 쪽이 기준인지 흐려진다.
                verify(exactly = 1) { jobs.create(match { it.anonymousId == null }) }
            }
        }

        When("비회원이 작업을 맡기면") {
            val jobs = mockk<JobPersistencePort>()
            val executor = mockk<AnalysisExecutorPort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), executor, anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)

            every { jobs.create(any()) } returns job(userId = null)
            every { executor.execute(any(), any(), any(), any(), any()) } just Runs

            val command = DeedCommand.Upload(
                file = MockMultipartFile("file", "deed.pdf", "application/pdf", byteArrayOf(1, 2)),
                fileName = "deed.pdf",
                fileSize = 2L,
                userId = null,
                leaseType = null,
                anonymousId = 브라우저A,
            )

            TransactionSynchronizationManager.initSynchronization()
            try {
                useCase.uploadDeed(command)
                TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }

            Then("회원 주인은 비우고, 업로드한 브라우저를 주인으로 남긴다") {
                // 회원 주인을 임의로 붙이면 남의 이력에 섞여 들어간다.
                // 브라우저를 남기지 않으면 결과를 jobId 만으로 누구나 열 수 있다.
                verify(exactly = 1) { jobs.create(match { it.userId == null && it.anonymousId == 브라우저A }) }
            }

            Then("분석도 주인 없이 시작된다") {
                // 이 값으로 푸시 대상을 찾으므로, 비어 있어야 발송을 건너뛴다.
                verify(exactly = 1) { executor.execute(any(), any(), any(), null, null) }
            }

            Then("하루 제한을 세지 않는다") {
                // 비회원은 셀 기준이 없다. 세려 들면 모두를 한 덩어리로 막게 된다.
                verify(exactly = 0) { jobs.countStartedSince(any(), any()) }
            }
        }

        When("오늘 제한만큼 이미 분석했으면") {
            val jobs = mockk<JobPersistencePort>()
            val executor = mockk<AnalysisExecutorPort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), executor, anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)

            every { jobs.countStartedSince(1L, any()) } returns 1L

            val command = DeedCommand.Upload(
                file = MockMultipartFile("file", "deed.pdf", "application/pdf", byteArrayOf(1, 2)),
                fileName = "deed.pdf",
                fileSize = 2L,
                userId = 1L,
            )

            Then("거절하고 작업을 만들지 않는다") {
                shouldThrow<BusinessException> { useCase.uploadDeed(command) }
                    .errorCode shouldBe ErrorCode.DAILY_LIMIT_EXCEEDED

                // 막기만 하고 작업이 남으면 이력이 더러워지고 분석 비용도 나간다.
                verify(exactly = 0) { jobs.create(any()) }
                verify(exactly = 0) { executor.execute(any(), any(), any(), any(), any()) }
            }
        }

        When("비회원이 같은 주소에서 제한만큼 이미 분석했으면") {
            val jobs = mockk<JobPersistencePort>()
            val executor = mockk<AnalysisExecutorPort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(
                jobs, mockk(), executor, anon,
                dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100,
            )

            every { anon.increaseClientUsage("1.2.3.4") } returns 2L

            Then("거절하고 천장은 깎지 않는다") {
                shouldThrow<BusinessException> {
                    useCase.uploadDeed(anonymousUpload(clientAddress = "1.2.3.4"))
                }.errorCode shouldBe ErrorCode.DAILY_LIMIT_EXCEEDED

                // 거절당할 요청이 전체 몫을 깎으면, 한 사람이 남의 몫까지 태울 수 있다.
                verify(exactly = 0) { anon.increaseTotalUsage() }
                verify(exactly = 0) { jobs.create(any()) }
            }
        }

        When("비회원 전체 천장을 넘겼으면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(
                jobs, mockk(), mockk(), anon,
                dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100,
            )

            every { anon.increaseClientUsage(any()) } returns 1L
            every { anon.increaseTotalUsage() } returns 101L

            Then("주소가 처음이어도 거절한다") {
                // 주소는 바꿀 수 있다. 천장이 닫히지 않으면 비용이 닫히지 않는다.
                shouldThrow<BusinessException> {
                    useCase.uploadDeed(anonymousUpload(clientAddress = "9.9.9.9"))
                }.errorCode shouldBe ErrorCode.DAILY_LIMIT_EXCEEDED

                verify(exactly = 0) { jobs.create(any()) }
            }
        }

        When("비회원 사용량을 셀 수 없으면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(
                jobs, mockk(), mockk(), anon,
                dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100,
            )

            every { anon.increaseClientUsage(any()) } returns null
            every { anon.increaseTotalUsage() } returns null

            Then("통과시키지 않고 막는다") {
                // 이 천장이 비용의 유일한 보장이다. 세지 못하는 동안 열어 두면 보장 자체가 사라진다.
                shouldThrow<BusinessException> {
                    useCase.uploadDeed(anonymousUpload(clientAddress = "1.2.3.4"))
                }.errorCode shouldBe ErrorCode.SERVICE_UNAVAILABLE

                verify(exactly = 0) { jobs.create(any()) }
            }
        }

        When("천장만 셀 수 없으면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(
                jobs, mockk(), mockk(), anon,
                dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100,
            )

            every { anon.increaseClientUsage(any()) } returns 1L
            every { anon.increaseTotalUsage() } returns null

            Then("주소별 한도를 통과했어도 막는다") {
                shouldThrow<BusinessException> {
                    useCase.uploadDeed(anonymousUpload(clientAddress = "1.2.3.4"))
                }.errorCode shouldBe ErrorCode.SERVICE_UNAVAILABLE

                verify(exactly = 0) { jobs.create(any()) }
            }
        }

        When("주소를 알 수 없는 비회원이 올리면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(
                jobs, mockk(), mockk(), anon,
                dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100,
            )

            every { anon.increaseTotalUsage() } returns 101L

            Then("주소별 한도는 건너뛰어도 천장은 센다") {
                // 주소가 없다고 통과시키면 헤더를 지우는 것만으로 천장을 비껴간다.
                shouldThrow<BusinessException> {
                    useCase.uploadDeed(anonymousUpload(clientAddress = null))
                }.errorCode shouldBe ErrorCode.DAILY_LIMIT_EXCEEDED

                verify(exactly = 0) { anon.increaseClientUsage(any()) }
                verify(exactly = 1) { anon.increaseTotalUsage() }
            }
        }

        When("하루 사용량을 셀 때") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.countStartedSince(any(), any()) } returns 1L

            Then("오늘 자정 이후만 센다") {
                // 24시간 전부터 세면 어제 늦게 쓴 사람이 오늘 종일 막힌다.
                shouldThrow<BusinessException> {
                    useCase.uploadDeed(
                        DeedCommand.Upload(
                            file = MockMultipartFile("file", "deed.pdf", "application/pdf", byteArrayOf(1)),
                            fileName = "deed.pdf",
                            fileSize = 1L,
                            userId = 1L,
                        )
                    )
                }
                verify(exactly = 1) { jobs.countStartedSince(1L, LocalDate.now().atStartOfDay()) }
            }
        }
    }

    Given("진행 상황 구독") {

        When("남의 작업을 구독하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = 99L)

            Then("접근을 막는다") {
                // 식별자를 알아도 접근할 수 없어야 한다
                shouldThrow<BusinessException> { useCase.streamJob("job-1", userId = 1L) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("비회원이 주인 있는 작업을 구독하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = 99L)

            Then("접근을 막는다") {
                // 비회원을 받기 시작하면서 열린 길이다. 식별자를 알아도 남의 것은 볼 수 없어야 한다.
                shouldThrow<BusinessException> { useCase.streamJob("job-1", userId = null) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("익명 쿠키를 쓰기 전에 만들어진 작업을 비회원이 구독하면") {
            val jobs = mockk<JobPersistencePort>()
            val sse = mockk<SseNotifierPort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, sse, mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)

            every { jobs.findByJobId("job-1") } returns job(userId = null)
            every { sse.createEmitter("job-1") } returns SseEmitter()

            Then("구독할 수 있다") {
                // 확인할 기준이 없는 옛 작업이다. 막으면 그때 만든 분석을 아무도 못 본다.
                useCase.streamJob("job-1", userId = null)
                verify(exactly = 1) { sse.createEmitter("job-1") }
            }
        }

        When("구독 시점에 분석이 이미 끝나 있으면") {
            val jobs = mockk<JobPersistencePort>()
            val sse = mockk<SseNotifierPort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, sse, mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)

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
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = 99L)

            Then("접근을 막는다") {
                shouldThrow<BusinessException> { useCase.getJob("job-1", userId = 1L) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("비회원이 주인 있는 작업을 조회하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = 99L)

            Then("접근을 막는다") {
                shouldThrow<BusinessException> { useCase.getJob("job-1", userId = null) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("익명 쿠키를 쓰기 전에 만들어진 작업을 조회하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = null)

            Then("비회원도 회원도 볼 수 있다") {
                // 확인할 기준이 없는 옛 작업이다. 식별자 자체가 열쇠로 남는다.
                useCase.getJob("job-1", userId = null).jobId shouldBe "job-1"
                useCase.getJob("job-1", userId = 1L).jobId shouldBe "job-1"
            }
        }

        When("비회원 작업을 업로드한 브라우저가 조회하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = null, anonymousId = 브라우저A)

            Then("볼 수 있다") {
                useCase.getJob("job-1", userId = null, anonymousId = 브라우저A).jobId shouldBe "job-1"
            }
        }

        When("비회원 작업을 다른 브라우저가 조회하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = null, anonymousId = 브라우저A)

            Then("식별자를 알아도 막는다") {
                // 등기부에는 주소와 소유자 이름이 들어 있다. 링크가 새면 그대로 노출된다.
                shouldThrow<BusinessException> { useCase.getJob("job-1", userId = null, anonymousId = 브라우저B) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }

            Then("쿠키가 아예 없어도 막는다") {
                shouldThrow<BusinessException> { useCase.getJob("job-1", userId = null, anonymousId = null) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }

            Then("회원이라도 막는다") {
                // 로그인했다고 남이 올린 비회원 분석을 볼 이유는 없다.
                shouldThrow<BusinessException> { useCase.getJob("job-1", userId = 1L, anonymousId = 브라우저B) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("비회원 작업을 다른 브라우저가 구독하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("job-1") } returns job(userId = null, anonymousId = 브라우저A)

            Then("진행 상황도 막는다") {
                // 조회만 막고 구독을 열어 두면 결과가 그대로 흘러간다.
                shouldThrow<BusinessException> { useCase.streamJob("job-1", userId = null, anonymousId = 브라우저B) }
                    .errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }

        When("없는 작업을 조회하려 하면") {
            val jobs = mockk<JobPersistencePort>()
            val anon = mockk<AnonymousUsagePort>(relaxed = true)
            val useCase = DeedUseCaseImpl(jobs, mockk(), mockk(), anon, dailyLimit = 1, anonymousDailyLimitPerClient = 1, anonymousDailyLimitTotal = 100)
            every { jobs.findByJobId("nope") } returns null

            Then("찾을 수 없다고 알린다") {
                shouldThrow<BusinessException> { useCase.getJob("nope", userId = 1L) }
                    .errorCode shouldBe ErrorCode.NOT_FOUND
            }
        }
    }
})
