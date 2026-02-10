# SafeHome API

등기부등본 PDF 분석 서비스

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
    ├── config/                       # Spring 설정
    ├── datasource/                   # Read/Write DataSource 라우팅
    ├── enums/                        # JobStatus, AnalysisStep
    ├── exception/                    # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── object/                       # BaseEntity (감사 필드)
    └── response/                     # ApiResponse (sealed class)
```

## API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/deed/analyze` | 등기부등본 PDF 분석 (SSE 응답) |
| POST | `/api/job/id` | Job UUID 생성 |
| GET | `/api/sample` | 샘플 목록 조회 |
| GET | `/api/sample/details/{id}` | 샘플 상세 조회 |
| POST | `/api/sample/pdf/parse` | PDF 텍스트 추출 |

## 설정 파일

| 파일 | 설명 |
|------|------|
| `application.yml` | 기본 설정 |
| `application-db.yml` | DB 설정 (H2, Read/Write 분리, HikariCP) |
| `application-swagger.yml` | OpenAPI 설정 (local/dev만 활성) |
| `application-websocket.yml` | WebSocket 설정 |

## 실행

```bash
./gradlew bootRun
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console

## 테스트

```bash
./gradlew test
```

- Kotest BDD BehaviorSpec (Given-When-Then)
- JUnit 5 Platform