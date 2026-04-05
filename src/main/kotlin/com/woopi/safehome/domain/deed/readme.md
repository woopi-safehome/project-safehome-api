# deed 도메인

등기부등본(PDF) 분석 요청을 받아 비동기로 처리하는 핵심 도메인.
Job 생성, PDF 검증/파싱, 비동기 실행, SSE 알림, 결과 저장을 모두 담당한다.

---

## 패키지 구조

```
deed/
├── adapter/
│   ├── inbound/web/
│   │   ├── DeedInboundWebAdapter         # REST 컨트롤러 (POST /api/deed/analyze)
│   │   └── dto/
│   │       └── DeedRequest               # 요청 DTO (Analyze: MultipartFile)
│   └── outbound/
│       ├── PdfBoxParserAdapter           # PdfParserPort 구현체 (PDFBox로 PDF 파싱)
│       ├── PdfValidationAdapter          # PdfValidationPort 구현체 (PDF 유효성 검증)
│       ├── SseNotifierAdapter            # SseNotifierPort 구현체 (SSE Emitter 관리)
│       └── persistence/
│           ├── JobPersistenceAdapter     # JobPersistencePort 구현체 (JPA 저장/조회)
│           └── jpa/
│               ├── AnalysisJobEntity     # JPA 엔티티 (BaseEntity 상속)
│               ├── AnalysisJobEntityMapper # Entity <-> Domain Model 변환
│               └── AnalysisJobRepository # Spring Data JPA Repository
│
├── application/
│   ├── port/
│   │   ├── inbound/
│   │   │   ├── DeedUseCase               # 등기부 분석 요청 인터페이스
│   │   │   ├── AnalysisExecutorPort      # 비동기 분석 실행 인터페이스
│   │   │   └── command/
│   │   │       └── DeedCommand           # UseCase 입력 커맨드 (Analyze)
│   │   └── outbound/
│   │       ├── JobPersistencePort        # Job CRUD 포트 (create/updateStatus/complete)
│   │       ├── SseNotifierPort           # SSE Emitter 발급 및 이벤트 전송 포트
│   │       ├── PdfParserPort             # PDF 파싱 포트 (ByteArray → DeedSections)
│   │       └── PdfValidationPort         # PDF 유효성 검증 포트 (ByteArray, contentType)
│   ├── service/
│   │   └── AnalysisAsyncProcessor        # AnalysisExecutorPort 구현체 (@Async 비동기 처리)
│   └── usecase/
│       └── DeedUseCaseImpl               # Job 생성 → SSE 연결 → 비동기 실행 트리거
│
└── domain/
    ├── exception/
    │   └── InvalidPdfException           # PDF 검증 실패 예외
    └── model/
        ├── AnalysisJob                   # 분석 Job 도메인 모델 (Create / Data)
        └── DeedSections                  # 등기부등본 섹션 모델 (표제부/갑구/을구)
```

---

## 요청 흐름

```
DeedInboundWebAdapter (POST /api/deed/analyze)
  → DeedUseCase.analyzeDeed(DeedCommand.Analyze)
    → DeedUseCaseImpl
        1. JobPersistencePort.create()       # Job DB 저장 (PENDING)
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
        5. updateAndNotify(IN_PROGRESS, POST_PROCESSING)
        6. JobPersistencePort.complete()     # 결과 저장 (COMPLETED)
        7. SseNotifierPort.notifyStep()      # COMPLETED 이벤트 전송
```

분석 진행 상태: `PENDING → IN_PROGRESS (PDF_PARSING → LLM_ANALYSIS → POST_PROCESSING) → COMPLETED / FAILED`

---

## 클래스 역할

### adapter/inbound

| 클래스 | 역할 |
|--------|------|
| `DeedInboundWebAdapter` | `POST /api/deed/analyze` 엔드포인트. `DeedRequest.Analyze`를 `DeedCommand.Analyze`로 변환해 UseCase 호출 |
| `DeedRequest` | 요청 DTO. `Analyze`: `MultipartFile` 포함 |

### adapter/outbound

| 클래스 | 역할 |
|--------|------|
| `PdfBoxParserAdapter` | Apache PDFBox로 PDF 바이트를 파싱해 `DeedSections` 반환 |
| `PdfValidationAdapter` | 바이트 배열 비어 있음 여부 및 `contentType == application/pdf` 검증 |
| `SseNotifierAdapter` | `ConcurrentHashMap<jobId, SseEmitter>` 관리. Emitter 생성 및 이벤트 전송 (타임아웃 5분) |
| `JobPersistenceAdapter` | `AnalysisJobRepository`를 통해 Job 생성/상태 갱신/완료 처리 |
| `AnalysisJobEntity` | `BaseEntity` 상속 JPA 엔티티 (jobId, fileName, fileSize, status, step, result, description) |
| `AnalysisJobEntityMapper` | `AnalysisJobEntity` ↔ `AnalysisJob.Data` 변환 |
| `AnalysisJobRepository` | Spring Data JPA Repository (`findByJobId`) |

### application/port/inbound

| 인터페이스 | 역할 |
|-----------|------|
| `DeedUseCase` | `analyzeDeed(DeedCommand.Analyze): SseEmitter` |
| `AnalysisExecutorPort` | `execute(jobId, file)` — 비동기 분석 실행 진입점 |
| `DeedCommand` | UseCase 입력 커맨드 객체. `Analyze(file, fileName, fileSize)` |

### application/port/outbound

| 인터페이스 | 역할 |
|-----------|------|
| `JobPersistencePort` | Job 생성(`create`), 상태 갱신(`updateStatus`), 완료(`complete`) |
| `SseNotifierPort` | Emitter 발급(`createEmitter`), 단계 이벤트 전송(`notifyStep`) |
| `PdfParserPort` | `parse(ByteArray): DeedSections` |
| `PdfValidationPort` | `validate(ByteArray, contentType?)` — 실패 시 `InvalidPdfException` |

### application/service & usecase

| 클래스 | 역할 |
|--------|------|
| `AnalysisAsyncProcessor` | `@Async` 비동기 분석 실행. PDF 검증 → 파싱 → 상태 갱신 → SSE 알림 순서로 진행 |
| `DeedUseCaseImpl` | Job 생성 후 SSE Emitter를 반환하고, 비동기 분석을 트리거 |

### domain

| 클래스 | 역할 |
|--------|------|
| `InvalidPdfException` | PDF 검증 실패 시 던지는 도메인 예외 |
| `AnalysisJob` | 분석 Job 도메인 모델. `Create` (생성 입력), `Data` (조회 결과) |
| `DeedSections` | 등기부등본 섹션 맵. `get(name)`, `hasSection(name)`, `sectionNames()` |
