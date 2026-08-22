# deed 도메인

등기부등본(PDF) 분석 요청을 받아 비동기로 처리하는 핵심 도메인.
Job 생성, PDF 검증/파싱, 비동기 실행, SSE 알림, 결과 저장, 분석 이력 조회를 모두 담당한다.

> **범위**: `domain/deed/**`
> **상위**: [`domain/README.md`](../README.md) (레이어 규칙) · [API README](../../../../../../../../README.md)
> **연관**: 캐시 정책 → `docs/llm-cache-strategy.md` · 엔드포인트 스펙 → 루트 README의 모듈 간 계약
> **검증**: 클래스 목록은 이 디렉토리 트리와 1:1, 시그니처는 각 포트 인터페이스와 대조

---

## 패키지 구조

```
deed/
├── adapter/
│   ├── inbound/web/
│   │   ├── DeedInboundWebAdapter         # REST 컨트롤러 (POST /api/deed/upload, GET /api/deed/jobs/{jobId}/stream, GET /api/deed/jobs/{jobId}, GET /api/deed/jobs)
│   │   └── dto/
│   │       ├── DeedRequest               # 요청 DTO (Upload: MultipartFile)
│   │       └── DeedResponse              # 응답 DTO (UploadResult: jobId, JobDetail: 분석 결과 포함, JobSummary: 목록용 요약)
│   └── outbound/
│       ├── PdfBoxParserAdapter           # PdfParserPort 구현체 (PDFBox로 PDF 파싱)
│       ├── PdfValidationAdapter          # PdfValidationPort 구현체 (PDF 유효성 검증)
│       ├── SseNotifierAdapter            # SseNotifierPort 구현체 (SSE Emitter 관리)
│       ├── LlmAnalysisAdapter            # LlmAnalysisPort 구현체 (AI API HTTP 호출)
│       ├── LlmCacheAdapter               # LlmCachePort 구현체 (Redis 캐시 조회/저장, TTL 7일)
│       ├── PigeonNotificationAdapter     # NotificationPort 구현체 (pigeon 서비스 HTTP 호출)
│       ├── UserDeviceQueryAdapter        # UserDeviceQueryPort 구현체 (auth 도메인 Repository 직접 사용)
│       └── persistence/
│           ├── JobPersistenceAdapter     # JobPersistencePort 구현체 (JPA 저장/조회)
│           └── jpa/
│               ├── AnalysisJobEntity     # JPA 엔티티 (BaseEntity 상속, user_id/safety_level/address 포함)
│               ├── AnalysisJobEntityMapper # Entity <-> Domain Model 변환
│               └── AnalysisJobRepository # Spring Data JPA Repository
│
├── application/
│   ├── port/
│   │   ├── inbound/
│   │   │   ├── DeedUseCase               # 등기부 분석 인터페이스 (uploadDeed/streamJob/getJob/getMyJobs)
│   │   │   ├── AnalysisExecutorPort      # 비동기 분석 실행 인터페이스
│   │   │   └── command/
│   │   │       └── DeedCommand           # UseCase 입력 커맨드 (Upload: userId 포함)
│   │   └── outbound/
│   │       ├── JobPersistencePort        # Job CRUD 포트 (create/findByJobId/findByUserId/updateStatus/complete)
│   │       ├── SseNotifierPort           # SSE Emitter 발급 및 이벤트 전송 포트
│   │       ├── PdfParserPort             # PDF 파싱 포트 (ByteArray → DeedSections)
│   │       ├── PdfValidationPort         # PDF 유효성 검증 포트 (ByteArray, contentType)
│   │       ├── LlmAnalysisPort           # LLM 분석 포트 (DeedSections → 분석 결과 JSON)
│   │       ├── LlmCachePort              # LLM 응답 캐시 포트 (섹션 해시 기반 Redis 캐시)
│   │       ├── NotificationPort          # FCM 푸시 발송 포트 (sendPush)
│   │       └── UserDeviceQueryPort       # 사용자 FCM 토큰 조회 포트 (findTokensByUserId)
│   ├── service/
│   │   └── AnalysisAsyncProcessor        # AnalysisExecutorPort 구현체 (@Async 비동기 처리, 완료 시 safetyLevel/address 추출)
│   └── usecase/
│       └── DeedUseCaseImpl               # Job 생성 → 비동기 실행 트리거(uploadDeed) / SSE 구독(streamJob), 소유권 검증 포함
│
└── domain/
    ├── exception/
    │   └── InvalidPdfException           # PDF 검증 실패 예외
    └── model/
        ├── AnalysisJob                   # 분석 Job 도메인 모델 (Create / Data — userId, safetyLevel, address, createdAt 포함)
        └── DeedSections                  # 등기부등본 섹션 모델 (표제부/갑구/을구)
```

---

## 요청 흐름

### ① PDF 업로드 (POST /api/deed/upload)

```
DeedInboundWebAdapter (POST /api/deed/upload)
  → DeedUseCase.uploadDeed(DeedCommand.Upload)
    → DeedUseCaseImpl
        1. JobPersistencePort.create()       # Job DB 저장 (PENDING, userId 포함)
        2. AnalysisExecutorPort.execute()    # 트랜잭션 커밋 후 비동기 분석 실행 트리거
        3. return jobId (String)             # 클라이언트에 jobId 즉시 반환
      ↓ (별도 스레드 — afterCommit)
    AnalysisAsyncProcessor (@Async)
        1. updateAndNotify(IN_PROGRESS, PDF_PARSING)
        2. PdfValidationPort.validate()      # PDF 유효성 검증
        3. PdfParserPort.parse()             # 섹션 파싱
        4. updateAndNotify(IN_PROGRESS, LLM_ANALYSIS)
        5. LlmCachePort.get(sectionHash)     # Redis 캐시 조회 (섹션 텍스트 SHA-256 해시)
           └ 캐시 미스 시: LlmAnalysisPort.analyze() → LlmCachePort.put()
        6. updateAndNotify(IN_PROGRESS, POST_PROCESSING)
        7. extractSummaryFields()            # result JSON에서 safetyLevel/address 추출
        8. JobPersistencePort.complete()     # 결과 저장 (COMPLETED, safetyLevel, address 포함)
        9. SseNotifierPort.notifyStep()      # COMPLETED 이벤트 전송
       10. userId != null 이면 FCM 푸시 발송
           ├─ UserDeviceQueryPort.findTokensByUserId(userId)  # 등록된 FCM 토큰 목록 조회
           └─ NotificationPort.sendPush(tokens, jobId)        # pigeon 서비스로 발송 요청
```

### ② SSE 구독 (GET /api/deed/jobs/{jobId}/stream)

```
DeedInboundWebAdapter (GET /api/deed/jobs/{jobId}/stream)
  → DeedUseCase.streamJob(jobId, userId)
    → DeedUseCaseImpl
        1. JobPersistencePort.findByJobId()  # Job 존재 확인 + 소유권 검증
        2. SseNotifierPort.createEmitter()   # SSE Emitter 발급 (jobId로 등록)
        3. 이미 COMPLETED/FAILED인 경우
           └ SseNotifierPort.notifyStep()    # 최종 상태 즉시 전송 후 emitter 닫음
        4. return SseEmitter                 # 클라이언트에 반환 (진행 중이면 비동기 알림 대기)
```

### ③ Job 조회 / 이력 목록

```
DeedInboundWebAdapter (GET /api/deed/jobs/{jobId})
  → DeedUseCase.getJob(jobId, userId)       # 소유권 검증 (job.userId != userId → FORBIDDEN)

DeedInboundWebAdapter (GET /api/deed/jobs)
  → DeedUseCase.getMyJobs(userId, pageable) # 내 분석 이력 목록 (최신순 페이징)
```

분석 진행 상태: `PENDING → IN_PROGRESS (PDF_PARSING → LLM_ANALYSIS → POST_PROCESSING) → COMPLETED / FAILED`

---

## 클래스 역할

### adapter/inbound

| 클래스 | 역할 |
|--------|------|
| `DeedInboundWebAdapter` | `POST /api/deed/upload` (PDF 업로드 → jobId 반환), `GET /api/deed/jobs/{jobId}/stream` (SSE 구독), `GET /api/deed/jobs/{jobId}` (결과 조회), `GET /api/deed/jobs` (내 이력 목록) |
| `DeedRequest` | 요청 DTO. `Upload`: `MultipartFile` + `leaseType` |
| `DeedResponse` | 응답 DTO. `UploadResult`: jobId / `JobDetail`: 분석 결과 포함(`result`는 `@JsonRawValue`) / `JobSummary`: 목록용 요약 (safetyLevel, address, createdAt, leaseType) |

### adapter/outbound

| 클래스 | 역할 |
|--------|------|
| `PdfBoxParserAdapter` | Apache PDFBox로 PDF 바이트를 파싱해 `DeedSections` 반환 |
| `PdfValidationAdapter` | 바이트 배열 비어 있음 여부 및 `contentType == application/pdf` 검증 |
| `SseNotifierAdapter` | `ConcurrentHashMap<jobId, SseEmitter>` 관리. Emitter 생성 및 이벤트 전송 (타임아웃 5분). 클라이언트 연결 끊김(`AsyncRequestNotUsableException`) 시 에러 처리 없이 정리 |
| `LlmAnalysisAdapter` | `RestTemplate`으로 AI API(`POST /api/deed/analyze`) 호출. `DeedSections` → 분석 결과 JSON String 반환 |
| `LlmCacheAdapter` | `StringRedisTemplate`으로 LLM 응답 캐싱. 키: `llm:deed:v2:{sha256}:{leaseType}`, TTL: 7일. Redis 장애 시 예외를 삼키고 캐시 미스처럼 동작 |
| `JobPersistenceAdapter` | `AnalysisJobRepository`를 통해 Job 생성/상태 갱신/완료 처리/유저별 목록 조회 |
| `PigeonNotificationAdapter` | `RestClient`로 pigeon 서비스(`POST /api/messages/send`) 호출. 각 FCM 토큰별 "분석 완료" 푸시 발송. 실패 시 error 로그 기록 후 무시. pigeon이 `TOKEN_UNREGISTERED(400)` 반환 시 해당 토큰을 `user_devices`에서 삭제 |
| `UserDeviceQueryAdapter` | auth 도메인의 `UserDeviceRepository`를 직접 주입받아 userId로 등록된 FCM 토큰 목록 조회(`findTokensByUserId`) 및 만료 토큰 삭제(`deleteByFcmToken`) |
| `AnalysisJobEntity` | `BaseEntity` 상속 JPA 엔티티 (jobId, fileName, fileSize, status, step, result, description, userId, safetyLevel, address) |
| `AnalysisJobEntityMapper` | `AnalysisJobEntity` ↔ `AnalysisJob.Data` 변환 |
| `AnalysisJobRepository` | Spring Data JPA Repository (`findByJobId`, `findByUserIdOrderByCreatedAtDesc`) |

### application/port/inbound

| 인터페이스 | 역할 |
|-----------|------|
| `DeedUseCase` | `uploadDeed(DeedCommand.Upload): String`, `streamJob(jobId, userId): SseEmitter`, `getJob(jobId, userId): AnalysisJob.Data`, `getMyJobs(userId, pageable): Page<AnalysisJob.Data>` |
| `AnalysisExecutorPort` | `execute(jobId, fileBytes, contentType, leaseType, userId)` — 비동기 분석 실행 진입점 |
| `DeedCommand` | UseCase 입력 커맨드 객체. `Upload(file, fileName, fileSize, userId, leaseType?)` |

### application/port/outbound

| 인터페이스 | 역할 |
|-----------|------|
| `JobPersistencePort` | Job 생성(`create`), 단건 조회(`findByJobId`), 유저별 목록 조회(`findByUserId`), 상태 갱신(`updateStatus`), 완료(`complete`) |
| `SseNotifierPort` | Emitter 발급(`createEmitter`), 단계 이벤트 전송(`notifyStep`) |
| `PdfParserPort` | `parse(ByteArray): DeedSections` |
| `PdfValidationPort` | `validate(ByteArray, contentType?)` — 실패 시 `InvalidPdfException` |
| `LlmAnalysisPort` | `analyze(sections, leaseType): String` — AI API 호출로 분석 결과 JSON 반환 |
| `LlmCachePort` | `get(sectionHash): String?`, `put(sectionHash, result)` — LLM 응답 캐시 인터페이스. `sectionHash`는 `{sha256}:{leaseType}` 형태 |
| `NotificationPort` | FCM 푸시 발송 포트 (`sendPush(fcmTokens, jobId)`) |
| `UserDeviceQueryPort` | 사용자 FCM 토큰 조회(`findTokensByUserId`) 및 만료 토큰 삭제(`deleteByFcmToken`) 포트 |

### application/service & usecase

| 클래스 | 역할 |
|--------|------|
| `AnalysisAsyncProcessor` | `@Async` 비동기 분석 실행. PDF 검증 → 파싱 → LLM 분석 → 상태 갱신 → SSE 알림. 완료 시 result JSON에서 safetyLevel/address 추출 저장 |
| `DeedUseCaseImpl` | PDF 업로드·Job 생성·비동기 트리거(`uploadDeed`), SSE Emitter 발급·완료 시 즉시 전송(`streamJob`), 소유권 검증 후 단건 조회(`getJob`), 유저별 목록 조회(`getMyJobs`) |

### domain

| 클래스 | 역할 |
|--------|------|
| `InvalidPdfException` | PDF 검증 실패 시 던지는 도메인 예외 |
| `AnalysisJob` | 분석 Job 도메인 모델. `Create` (userId 포함), `Data` (userId, safetyLevel, address, createdAt 포함) |
| `DeedSections` | 등기부등본 섹션 맵. `get(name)`, `hasSection(name)`, `sectionNames()` |
