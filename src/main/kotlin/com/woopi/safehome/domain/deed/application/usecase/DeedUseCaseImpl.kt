package com.woopi.safehome.domain.deed.application.usecase

import com.woopi.safehome.domain.deed.application.port.inbound.DeedUseCase
import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.AnonymousUsagePort
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDate
import java.util.*

@Transactional(readOnly = true)
@Service
class DeedUseCaseImpl(
    private val jobPersistencePort: JobPersistencePort,
    private val sseNotifierPort: SseNotifierPort,
    private val analysisExecutorPort: AnalysisExecutorPort,
    private val anonymousUsagePort: AnonymousUsagePort,
    @Value("\${safehome.analysis.daily-limit}") private val dailyLimit: Int,
    @Value("\${safehome.analysis.anonymous.daily-limit-per-client}") private val anonymousDailyLimitPerClient: Int,
    @Value("\${safehome.analysis.anonymous.daily-limit-total}") private val anonymousDailyLimitTotal: Int,
) : DeedUseCase {

    /**
     * 하루 제한을 확인한다. **계정 기준이다.**
     *
     * 하루의 경계는 서버의 자정이다 — 이 서비스의 시각 표기가 오프셋 없는 로컬 시각이라
     * 다른 기준을 쓰면 클라이언트가 보는 날짜와 어긋난다.
     *
     * **비회원은 세지 않는다.** 셀 기준이 없기 때문이다 — 같은 사람인지 알 방법이 없으므로
     * 여기서 막으면 모두를 한 덩어리로 막거나 아무도 못 막거나 둘 중 하나가 된다.
     */
    private fun assertWithinDailyLimit(userId: Long?) {
        if (userId == null) return

        val startedToday = jobPersistencePort.countStartedSince(userId, LocalDate.now().atStartOfDay())
        if (startedToday >= dailyLimit) throw BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED)
    }

    /**
     * 비회원의 하루 제한. **주소를 단서로 세고, 비회원 전체에도 천장을 둔다.**
     *
     * 주소는 바꿀 수 있다 — VPN 이나 회선 전환이면 그만이다. 그래서 주소 제한만으로는
     * 비용이 닫히지 않고, **전체 천장이 그 몫을 한다.** 반대로 천장만 두면
     * 한 사람이 남의 몫까지 다 쓸 수 있으므로 둘이 같이 있어야 한다.
     *
     * **회원은 이 천장에 세지 않는다.** 비회원이 몰려도 회원의 하루치는 남아 있어야 한다.
     *
     * **셀 수 없으면 통과시킨다.** 이 저장소에서 캐시는 가용성의 전제가 아니다 —
     * 저장소가 흔들렸다고 서비스를 멈추지 않는다. 그동안 제한이 열린다는 뜻이기도 하다.
     */
    private fun assertWithinAnonymousLimit(clientAddress: String?) {
        if (clientAddress == null) return

        val byClient = anonymousUsagePort.increaseClientUsage(clientAddress)
        if (byClient != null && byClient > anonymousDailyLimitPerClient) {
            throw BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED)
        }

        // 주소별로 먼저 막고 나서 천장을 센다. 순서가 반대면 거절당할 요청이 천장을 깎는다.
        val total = anonymousUsagePort.increaseTotalUsage()
        if (total != null && total > anonymousDailyLimitTotal) {
            throw BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED)
        }
    }

    @Transactional
    override fun uploadDeed(command: DeedCommand.Upload): String {
        if (command.userId == null) {
            assertWithinAnonymousLimit(command.clientAddress)
        } else {
            assertWithinDailyLimit(command.userId)
        }

        val jobId = UUID.randomUUID().toString()

        jobPersistencePort.create(
            AnalysisJob.Create(
                jobId = jobId,
                fileName = command.fileName,
                fileSize = command.fileSize,
                status = JobStatus.PENDING,
                userId = command.userId,
                // 회원 작업에는 익명 주인을 남기지 않는다. 주인이 둘이면 어느 쪽이 기준인지 흐려진다.
                anonymousId = if (command.userId == null) command.anonymousId else null,
                leaseType = command.leaseType,
            )
        )

        val fileBytes = command.file.bytes
        val contentType = command.file.contentType

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                analysisExecutorPort.execute(jobId, fileBytes, contentType, command.leaseType, command.userId)
            }
        })

        return jobId
    }

    /**
     * 이 작업을 볼 수 있는지 확인한다.
     *
     * 회원 작업은 본인만, **비회원 작업은 업로드한 브라우저(익명 쿠키)만** 볼 수 있다.
     * 등기부에는 주소와 소유자 이름이 들어 있어, jobId 만으로 열면 링크가 새는 순간 그대로 노출된다.
     *
     * **주인이 아예 없는 작업은 예외다** — 익명 쿠키를 쓰기 전에 만들어진 것들이라
     * 확인할 기준이 없다. 그때는 종전대로 jobId 가 열쇠다. 새로 만들어지는 작업에는 해당하지 않는다.
     */
    private fun assertReadable(job: AnalysisJob.Data, userId: Long?, anonymousId: String?) {
        if (job.userId != null) {
            if (job.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN)
            return
        }
        if (job.anonymousId == null) return
        if (job.anonymousId != anonymousId) throw BusinessException(ErrorCode.FORBIDDEN)
    }

    override fun streamJob(jobId: String, userId: Long?, anonymousId: String?): SseEmitter {
        val job = jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        assertReadable(job, userId, anonymousId)

        val emitter = sseNotifierPort.createEmitter(jobId)

        // 이미 분석이 끝난 경우 — 즉시 최종 상태를 전송하고 emitter를 닫음
        if (job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED) {
            sseNotifierPort.notifyStep(
                jobId = jobId,
                status = job.status,
                step = job.step,
                message = job.description ?: "분석이 완료되었습니다.",
            )
        }

        return emitter
    }

    override fun getJob(jobId: String, userId: Long?, anonymousId: String?): AnalysisJob.Data {
        val job = jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        assertReadable(job, userId, anonymousId)

        return job
    }

    override fun getMyJobs(userId: Long, pageable: Pageable): Page<AnalysisJob.Data> {
        return jobPersistencePort.findByUserId(userId, pageable)
    }
}
