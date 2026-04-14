# SafeHome API

등기부등본 PDF 분석 서비스의 Spring Boot REST API 서버입니다.

## 기술 스택

| 항목 | 버전 |
|------|------|
| Kotlin | 2.1.0 |
| Spring Boot | 3.5.8 |
| JDK | 21 |
| Gradle | Kotlin DSL |
| H2 Database | MySQL 호환 모드 |
| Apache PDFBox | 3.0.3 |
| SpringDoc OpenAPI | 2.8.9 |
| Kotest | 5.9.1 |

## 아키텍처

헥사고날 아키텍처 (Ports & Adapters) 기반의 DDD 구조

```
Client → Inbound Adapter (Controller)
       → Inbound Port (UseCase interface)
       → Application Service (UseCase impl)
       → Domain Service / Model
       → Outbound Port (interface)
       → Outbound Adapter (Persistence / SSE / Async)
```

## 도메인

| 도메인 | 설명 |
|--------|------|
| `deed` | 등기부등본 PDF 분석 (핵심 도메인) |
| `analysisjob` | 비동기 분석 처리 + SSE 실시간 알림 |
| `_sample` | CRUD 참조 구현 |

## 패키지 구조

```
src/main/kotlin/com/woopi/safehome/
├── domain/
│   └── {domainName}/
│       ├── adapter/
│       │   ├── inbound/web/          # REST Controller
│       │   └── outbound/
│       │       ├── persistence/      # JPA Entity, Repository, Adapter
│       │       └── sse/              # SSE Notifier
│       ├── application/
│       │   ├── port/
│       │   │   ├── inbound/          # UseCase 인터페이스
│       │   │   └── outbound/         # Persistence/Executor Port
│       │   ├── usecase/              # UseCase 구현체
│       │   └── service/              # Application Service
│       ├── domain/service/           # Domain Service
│       └── model/                    # Domain Model
└── global/
    ├── config/                       # Spring 설정 (CORS, Async, JPA 등)
    ├── datasource/                   # Read/Write DataSource 라우팅
    ├── enums/                        # JobStatus, AnalysisStep
    ├── exception/                    # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── object/                       # BaseEntity (감사 필드)
    └── response/                     # ApiResponse (sealed class)
```

## API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/deed/analyze` | 등기부등본 PDF 업로드 및 분석 시작 (SSE 스트리밍 응답) |
| GET | `/api/deed/jobs/{jobId}` | 분석 Job 상태 및 결과 조회 |

### POST /api/deed/analyze

- Content-Type: `multipart/form-data`
- 응답: `text/event-stream` (SSE)
- 분석 진행 단계마다 SSE 이벤트 전송

**SSE 이벤트 형식**

```json
{
  "jobId": "uuid",
  "status": "PENDING | IN_PROGRESS | COMPLETED | FAILED",
  "step": "PDF_PARSING | LLM_ANALYSIS | POST_PROCESSING | null",
  "message": "진행 상태 메시지",
  "timestamp": "2026-04-15T10:00:00"
}
```

**분석 단계 흐름**

```
PENDING         → 분석 작업이 시작되었습니다.
IN_PROGRESS     → 첨부된 파일을 분석중이에요  (PDF_PARSING)
IN_PROGRESS     → AI가 등본을 분석중이에요    (LLM_ANALYSIS)
IN_PROGRESS     → 분석한 내용을 정리중이에요  (POST_PROCESSING)
COMPLETED       → 완료 됐습니다!
FAILED          → 오류 메시지                 (각 단계에서 발생 가능)
```

### GET /api/deed/jobs/{jobId}

**응답 예시**

```json
{
  "type": "success",
  "data": {
    "jobId": "uuid",
    "fileName": "등기부등본.pdf",
    "fileSize": 102400,
    "status": "COMPLETED",
    "step": "POST_PROCESSING",
    "description": null,
    "result": "{\"isValidDeed\":true,\"safetyLevel\":\"SAFE\",...}"
  }
}
```

> `result`는 분석 결과 JSON을 문자열로 직렬화한 값입니다. 클라이언트에서 `JSON.parse()`하여 사용합니다.

## 설정 파일

| 파일 | 설명 |
|------|------|
| `application.yml` | 기본 설정 |
| `application-db.yml` | DB 설정 (H2, Read/Write 분리, HikariCP) |
| `application-swagger.yml` | OpenAPI 설정 |
| `application-ai.yml` | AI API URL 설정 (`safehome.ai-api.url`) |

## 로컬 실행

### 사전 조건

- JDK 21
- AI API 서버(`project-safehome-ai-api`)가 `http://localhost:5000`에서 실행 중이어야 합니다.

### 실행

```bash
./gradlew bootRun
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console

### 전체 실행 순서

```bash
# 1. AI API 먼저 시작 (필수)
cd project-safehome-ai-api && python app.py

# 2. API 서버 시작
cd project-safehome-api && ./gradlew bootRun
```

AI API 없이 API 서버만 시작하면 LLM 분석 단계에서 `FAILED` 이벤트가 발생합니다.

### CORS

웹 브라우저 클라이언트(`localhost:8081`)에서 호출 가능하도록 CORS가 설정되어 있습니다 (`global/config/AsyncConfig.kt`).

## 테스트

```bash
./gradlew test
```

- Kotest BDD BehaviorSpec (Given-When-Then)
- JUnit 5 Platform
