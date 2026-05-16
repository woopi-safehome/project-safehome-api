# deed 도메인

등기부등본(PDF) 분석 요청을 받아 비동기로 처리하는 핵심 도메인.
Job 생성, PDF 검증/파싱, 비동기 실행, SSE 알림, 결과 저장, 분석 이력 조회를 모두 담당한다.

---

## 패키지 구조

```
deed/
├── adapter/
│   ├── inbound/web/
│   │   ├── DeedInboundWebAdapter         # REST 컨트롤러 (POST /api/deed/analyze, GET /api/deed/jobs/{jobId}, GET /api/deed/jobs)
│   │   └── dto/
│   │       ├── DeedRequest               # 요청 DTO (Analyze: MultipartFile)
│   │       └── DeedResponse              # 응답 DTO (JobDetail: 분석 결과 포함, JobSummary: 목록용 요약)
│   └── outbound/
│       ├── PdfBoxParserAdapter           # PdfParserPort 구현체 (PDFBox로 PDF 파싱)
│       ├── PdfValidationAdapter          # PdfValidationPort 구현체 (PDF 유효성 검증)
│       ├── SseNotifierAdapter            # SseNotifierPort 구현체 (SSE Emitter 관리)
│       ├── LlmAnalysisAdapter            # LlmAnalysisPort 구현체 (AI API HTTP 호출)
│       ├── LlmCacheAdapter               # LlmCachePort 구현체 (Redis 캐시 조회/저장, TTL 30일)
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
│   │   │   ├── DeedUseCase               # 등기부 분석 요청 인터페이스 (analyzeDeed/getJob/getMyJobs)
│   │   │   ├── AnalysisExecutorPort      # 비동기 분석 실행 인터페이스
│   │   │   └── command/
│   │   │       └── DeedCommand           # UseCase 입력 커맨드 (Analyze: userId 포함)
│   │   └── outbound/
│   │       ├── JobPersistencePort        # Job CRUD 포트 (create/findByJobId/findByUserId/updateStatus/complete)
│   │       ├── SseNotifierPort           # SSE Emitter 발급 및 이벤트 전송 포트
│   │       ├── PdfParserPort             # PDF 파싱 포트 (ByteArray → DeedSections)
│   │       ├── PdfValidationPort         # PDF 유효성 검증 포트 (ByteArray, contentType)
│   │       ├── LlmAnalysisPort           # LLM 분석 포트 (DeedSections → 분석 결과 JSON)
│   │       └── LlmCachePort              # LLM 응답 캐시 포트 (섹션 해시 기반 Redis 캐시)
│   ├── service/
│   │   └── AnalysisAsyncProcessor        # AnalysisExecutorPort 구현체 (@Async 비동기 처리, 완료 시 safetyLevel/address 추출)
│   └── usecase/
│       └── DeedUseCaseImpl               # Job 생성 → SSE 연결 → 비동기 실행 트리거, 소유권 검증 포함
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

```
DeedInboundWebAdapter (POST /api/deed/analyze)
  → DeedUseCase.analyzeDeed(DeedCommand.Analyze)
    → DeedUseCaseImpl
        1. JobPersistencePort.create()       # Job DB 저장 (PENDING, userId 포함)
        2. SseNotifierPort.createEmitter()   # SSE Emitter 발급
        3. SseNotifierPort.notifyStep()      # PENDING 이벤트 전송
        4. AnalysisExecutorPort.execute()    # 비동기 분석 실행 트리거
        5. return SseEmitter                 # 클라이언트에 즉시 반환
      ↓ (별도 스레드)
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
| `DeedInboundWebAdapter` | `POST /api/deed/analyze` (분석 시작), `GET /api/deed/jobs/{jobId}` (결과 조회), `GET /api/deed/jobs` (내 이력 목록) |
| `DeedRequest` | 요청 DTO. `Analyze`: `MultipartFile` 포함 |
| `DeedResponse` | 응답 DTO. `JobDetail`: 분석 결과 포함 / `JobSummary`: 목록용 요약 (safetyLevel, address, createdAt) |

### adapter/outbound

| 클래스 | 역할 |
|--------|------|
| `PdfBoxParserAdapter` | Apache PDFBox로 PDF 바이트를 파싱해 `DeedSections` 반환 |
| `PdfValidationAdapter` | 바이트 배열 비어 있음 여부 및 `contentType == application/pdf` 검증 |
| `SseNotifierAdapter` | `ConcurrentHashMap<jobId, SseEmitter>` 관리. Emitter 생성 및 이벤트 전송 (타임아웃 5분) |
| `LlmAnalysisAdapter` | `RestTemplate`으로 AI API(`POST /api/deed/analyze`) 호출. `DeedSections` → 분석 결과 JSON String 반환 |
| `LlmCacheAdapter` | `StringRedisTemplate`으로 LLM 응답 캐싱. 키: `llm:deed:{sha256}`, TTL: 7일 |
| `JobPersistenceAdapter` | `AnalysisJobRepository`를 통해 Job 생성/상태 갱신/완료 처리/유저별 목록 조회 |
| `AnalysisJobEntity` | `BaseEntity` 상속 JPA 엔티티 (jobId, fileName, fileSize, status, step, result, description, userId, safetyLevel, address) |
| `AnalysisJobEntityMapper` | `AnalysisJobEntity` ↔ `AnalysisJob.Data` 변환 |
| `AnalysisJobRepository` | Spring Data JPA Repository (`findByJobId`, `findByUserIdOrderByCreatedAtDesc`) |

### application/port/inbound

| 인터페이스 | 역할 |
|-----------|------|
| `DeedUseCase` | `analyzeDeed(DeedCommand.Analyze): SseEmitter`, `getJob(jobId, userId): AnalysisJob.Data`, `getMyJobs(userId, pageable): Page<AnalysisJob.Data>` |
| `AnalysisExecutorPort` | `execute(jobId, file, leaseType)` — 비동기 분석 실행 진입점 |
| `DeedCommand` | UseCase 입력 커맨드 객체. `Analyze(file, fileName, fileSize, userId, leaseType?)` |

### application/port/outbound

| 인터페이스 | 역할 |
|-----------|------|
| `JobPersistencePort` | Job 생성(`create`), 단건 조회(`findByJobId`), 유저별 목록 조회(`findByUserId`), 상태 갱신(`updateStatus`), 완료(`complete`) |
| `SseNotifierPort` | Emitter 발급(`createEmitter`), 단계 이벤트 전송(`notifyStep`) |
| `PdfParserPort` | `parse(ByteArray): DeedSections` |
| `PdfValidationPort` | `validate(ByteArray, contentType?)` — 실패 시 `InvalidPdfException` |
| `LlmAnalysisPort` | `analyze(DeedSections): String` — AI API 호출로 분석 결과 JSON 반환 |
| `LlmCachePort` | `get(sectionHash): String?`, `put(sectionHash, result)` — LLM 응답 캐시 인터페이스 |

### application/service & usecase

| 클래스 | 역할 |
|--------|------|
| `AnalysisAsyncProcessor` | `@Async` 비동기 분석 실행. PDF 검증 → 파싱 → LLM 분석 → 상태 갱신 → SSE 알림. 완료 시 result JSON에서 safetyLevel/address 추출 저장 |
| `DeedUseCaseImpl` | Job 생성 + SSE 트리거(`analyzeDeed`), 소유권 검증 후 단건 조회(`getJob`), 유저별 목록 조회(`getMyJobs`) |

### domain

| 클래스 | 역할 |
|--------|------|
| `InvalidPdfException` | PDF 검증 실패 시 던지는 도메인 예외 |
| `AnalysisJob` | 분석 Job 도메인 모델. `Create` (userId 포함), `Data` (userId, safetyLevel, address, createdAt 포함) |
| `DeedSections` | 등기부등본 섹션 맵. `get(name)`, `hasSection(name)`, `sectionNames()` |
