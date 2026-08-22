# SafeHome API

등기부등본 분석 서비스의 **메인 백엔드**. 인증(카카오+JWT), PDF 파싱, 분석 Job 관리, LLM 응답 캐싱, SSE 실시간 알림, 푸시 발송 트리거를 담당한다.
AI 분석 자체는 하지 않는다 — AI API에 위임한다.

> **범위**: `project-safehome-api/**`
> **연관**: [루트 README](../README.md) (모듈 간 계약·기동 순서) · [AI API README](../project-safehome-ai-api/README.md)
> **검증**: 이 문서의 엔드포인트는 `*InboundWebAdapter`, 설정값은 `src/main/resources/application-*.yml`과 대조

---

## TL;DR

| 항목 | 값 |
|------|-----|
| 스택 | Kotlin 2.1.0 / Spring Boot 3.5.8 / JDK 21 / Gradle Kotlin DSL |
| 아키텍처 | 헥사고날 (Ports & Adapters) + DDD |
| DB | `local`: H2 파일(MySQL 모드) · `dev`/`prd`: PostgreSQL Primary/Replica |
| 캐시 | Redis (`local`은 임베디드) |
| 진입점 | `SafehomeApplication.kt` → `domain/{auth,deed}/adapter/inbound/web/` |
| 주요 라이브러리 | PDFBox 3.0.3, jjwt 0.12.6, SpringDoc 2.8.9, Sentry 7.14.0, Kotest 5.9.1 |

```bash
./gradlew bootRun                     # :8080 (local 프로파일)
./gradlew build -x test               # 빌드
./gradlew test                        # 전체 테스트
./gradlew test --tests "*ClassName"   # 단일 클래스
```

Swagger: http://localhost:8080/swagger-ui.html · H2 Console: http://localhost:8080/h2-console

---

## 작업 레시피

| 하려는 일 | 건드릴 파일 (순서대로) |
|-----------|----------------------|
| **새 엔드포인트 추가** | `application/port/inbound/{X}UseCase` → `application/usecase/{X}UseCaseImpl` → `adapter/inbound/web/{X}InboundWebAdapter` → `adapter/inbound/web/dto/` → 도메인 `README.md` |
| **새 외부 시스템 연동** | `application/port/outbound/{X}Port` (인터페이스 먼저) → `adapter/outbound/{X}Adapter` → `application-*.yml`에 URL 추가 → `resources/README.md` |
| **새 도메인 추가** | `domain/_sample/` 통째로 복사 → 이름 변경 → `domain/README.md` 도메인 목록에 추가 |
| **분석 단계 추가/변경** | `global/enums/AnalysisStep` → `application/service/AnalysisAsyncProcessor` → 루트 README의 SSE 계약 → App의 `AnalysisStep` enum |
| **DB 컬럼 추가** | `resources/init/postgresql/schema.sql` + `init/h2db/schema.sql` **양쪽** → `*Entity` → `*EntityMapper` → 도메인 모델 |
| **에러 코드 추가** | `global/exception/ErrorCode` → 루트 README의 에러 코드 목록 |
| **캐시 동작 변경** | `adapter/outbound/LlmCacheAdapter` → `docs/llm-cache-strategy.md` |

---

## 구조

```
src/main/kotlin/com/woopi/safehome/
├── SafehomeApplication.kt
├── domain/                  # 도메인별 헥사곤 → domain/README.md
│   ├── auth/                # 카카오 로그인, JWT, 회원탈퇴, FCM 디바이스 등록
│   ├── deed/                # 등기부등본 분석 (핵심 도메인)
│   └── _sample/             # CRUD 참조 구현 — 새 도메인의 템플릿
└── global/                  # 횡단 관심사 → global/README.md
    ├── aop/ auth/ config/ datasource/
    └── enums/ exception/ jwt/ object/ response/
```

각 도메인은 `adapter`(기술) / `application`(유스케이스·포트) / `domain`(순수 로직) 3계층이다.
레이어 책임·의존 규칙·보일러플레이트 → **[`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md)**

---

## API 엔드포인트

요청/응답 스펙과 SSE 이벤트 형식은 **[루트 README의 모듈 간 API 계약](../README.md#모듈-간-api-계약)** 이 원본이다. 여기는 구현 위치만 매핑한다.

| 엔드포인트 | 구현 클래스 | 인증 |
|-----------|------------|:---:|
| `POST /api/auth/kakao` | `AuthInboundWebAdapter` | — |
| `POST /api/auth/refresh` | `AuthInboundWebAdapter` | — |
| `POST /api/users/devices` | `UserInboundWebAdapter` | 🔒 |
| `DELETE /api/users/me` | `UserInboundWebAdapter` | 🔒 |
| `POST /api/deed/upload` | `DeedInboundWebAdapter` | 🔒 |
| `GET /api/deed/jobs/{jobId}/stream` | `DeedInboundWebAdapter` | 🔒 |
| `GET /api/deed/jobs/{jobId}` | `DeedInboundWebAdapter` | 🔒 |
| `GET /api/deed/jobs` | `DeedInboundWebAdapter` | 🔒 |

🔒 = `@CurrentUser userId: Long` 파라미터로 인증. Spring Security를 쓰지 않고 `CurrentUserArgumentResolver`가 `Authorization: Bearer` 헤더를 직접 해석한다.

---

## 핵심 패턴

### 응답 / 예외

```kotlin
ApiResponse.success(data)                    // { type: "success", data, message }
throw BusinessException(ErrorCode.NOT_FOUND) // → GlobalExceptionHandler → { type: "error", code, message }
```
`ErrorCode` enum이 HTTP status·코드·기본 메시지를 한 곳에서 관리한다.

### Read/Write DataSource 라우팅

```
@Transactional(readOnly = true) → DataSourceContextHolder(READ)  → Replica
@Transactional                  → DataSourceContextHolder(WRITE) → Primary
```
`DataSourceTransactionInterceptor` → `RoutingDataSource`. 상세 → [`global/README.md`](src/main/kotlin/com/woopi/safehome/global/README.md)

### 비동기 분석

`DeedUseCaseImpl`이 Job을 저장하고 **트랜잭션 커밋 후**(`afterCommit`) `AnalysisAsyncProcessor.execute()`를 `@Async`로 띄운다.
스레드풀은 `AsyncConfig`의 `analysisTaskExecutor` (core 4 / max 8 / queue 50).
커밋 전에 띄우면 비동기 스레드가 아직 없는 Job을 조회하게 되므로 순서를 바꾸면 안 된다.

---

## 데이터베이스

| 테이블 | 용도 | 비고 |
|--------|------|------|
| `analysis_jobs` | 분석 Job | `job_id` UNIQUE, `result`는 AI 응답 JSON 문자열 |
| `users` | 카카오 회원 | `kakao_id` UNIQUE, 탈퇴는 `is_deleted` 소프트 딜리트 |
| `user_devices` | FCM 토큰 | `fcm_token` UNIQUE → 재등록 시 upsert |
| `samples`, `sample_details` | `_sample` 도메인용 | 운영 기능 아님 |

- 스키마는 **Flyway가 아니라** `spring.sql.init`으로 적용된다. `ddl-auto: none` 고정.
- 스키마 파일이 **H2용·PostgreSQL용 2벌**(`resources/init/h2db/`, `resources/init/postgresql/`)이다. 컬럼 추가 시 양쪽 모두 수정해야 한다.
- 모든 테이블은 `BaseEntity`의 감사 컬럼(`created_id/at`, `updated_id/at`)을 가진다.

---

## 설정

프로파일별 설정값·환경변수 전체 목록 → **[`resources/README.md`](src/main/resources/README.md)**

| 프로파일 | DB | Redis | Swagger | 로그 |
|---------|-----|-------|---------|------|
| `local` | H2 파일 | 임베디드 | `/swagger-ui.html` | DEBUG |
| `dev` | PostgreSQL P/R | 외부 | `/api/swagger-ui.html` | DEBUG |
| `prd` | PostgreSQL P/R | 외부 | 비활성 *(아래 함정 참고)* | ERROR |

`local`은 환경변수 없이 그대로 실행된다. 카카오 로그인을 쓰려면 `KAKAO_ADMIN_KEY`가 필요하다.

---

## 배포

`develop` push → GitHub Actions(`.github/workflows/deploy-api-dev.yml`) → `ghcr.io` → SSH → 컨테이너 교체.

```
빌드(JDK21) → 이미지 push(ghcr.io/<owner>/safehome-api:dev)
  → SSH(appleboy/ssh-action) → docker compose pull → up -d --no-deps
  → /actuator/health 통과 시 완료
```

| 항목 | 값 |
|------|-----|
| 배포 경로 (서버) | `/home/woopi/project/safehome/api` |
| 환경변수 파일 (서버) | `/home/<user>/project/safehome/env/.env_api` |
| 이미지 태그 | `:dev` (최신) / `:dev-previous` (롤백용) — ghcr 무료 한도 500MB라 2개만 유지 |
| Docker 네트워크 | `safehome-net` (최초 1회 `docker network create`) |
| 컴포즈 파일 | `docker/dev/` (dev) · `docker/prod/` (운영, 백업 스크립트 포함) |

**GitHub Secrets** (Environment: `dev`): `DEV_SSH_HOST` `DEV_SSH_USER` `DEV_SSH_PRIVATE_KEY` `DEV_SSH_PORT`

**롤백**: 서버에서 compose 파일의 태그를 `:dev-previous`로 바꾸고 `docker compose up -d --no-deps safehome-api`.

**DB 컨테이너**: `safehome-{dev|prod}-primary`(5432) / `-replica`(5433). API 컨테이너만 워크플로우가 교체하고 DB는 최초 1회 수동 기동한다.
운영 백업은 `docker/prod/backup/backup.sh` (crontab 등록, 기본 보관 7일 — `.env`의 `BACKUP_RETENTION_DAYS`).

> **PostgreSQL을 쓰는 이유**: AI 분석 결과를 JSON으로 다루는 구조에서 `jsonb` 인덱싱이 강력하고, H2와 문법 차이가 적어 마이그레이션 부담이 낮으며, BSD 라이선스로 종속성이 없다.

---

## 함정 & 결정 이유

| 함정 | 내용 |
|------|------|
| **`prod` vs `prd` 프로파일** | `application-swagger.yml`만 `on-profile: prod`이고 나머지는 전부 `prd`다. 현재 `prd`로 뜨면 Swagger 비활성 설정이 적용되지 않는다. 수정 시 `prd`로 통일할 것 |
| **스키마 파일 2벌** | H2용·PostgreSQL용을 따로 관리한다. 한쪽만 고치면 로컬은 되는데 dev에서 깨진다 |
| **`@JsonRawValue`** | `JobDetail.result`는 문자열 필드지만 JSON 원본으로 내려간다. 클라이언트가 이중 파싱해야 할 수 있다 |
| **SSE 연결 끊김** | `AsyncRequestNotUsableException`은 클라이언트가 먼저 끊은 정상 케이스다. 에러로 처리하지 말 것 (`SseNotifierAdapter`) |
| **푸시 실패는 무시** | 분석 결과 저장이 끝난 뒤 발송하므로 pigeon 장애가 분석을 실패시키면 안 된다 |
| **Redis 장애도 무시** | `LlmCacheAdapter`는 예외를 삼키고 캐시 미스처럼 동작한다. AI API 호출로 폴백 |
| **`deed → auth` 의존** | 단방향만 허용. FCM 토큰 조회는 `UserDeviceQueryPort` + `UserDeviceQueryAdapter`로 격리했다. auth는 deed를 모른다 |
| **캐시 무효화 없음** | 등기부는 문서마다 고유해 히트율이 낮다. 전체 무효화가 필요하면 `KEY_PREFIX` 버전을 올린다 (`v2`→`v3`). 이유 → [`docs/llm-cache-strategy.md`](docs/llm-cache-strategy.md) |

---

## 테스트

```bash
./gradlew test
```
Kotest `BehaviorSpec` (Given/When/Then) + JUnit 5 Platform.

---

## 문서 지도

| 알고 싶은 것 | 문서 |
|-------------|------|
| 도메인 공통 구조·레이어 책임·의존 규칙 | [`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md) |
| auth 도메인 상세 | [`domain/auth/README.md`](src/main/kotlin/com/woopi/safehome/domain/auth/README.md) |
| deed 도메인 상세 | [`domain/deed/README.md`](src/main/kotlin/com/woopi/safehome/domain/deed/README.md) |
| 공통 인프라 (설정·예외·JWT·DataSource) | [`global/README.md`](src/main/kotlin/com/woopi/safehome/global/README.md) |
| 프로파일·환경변수 전체 | [`resources/README.md`](src/main/resources/README.md) |
| LLM 캐시 키·TTL·무효화 정책 | [`docs/llm-cache-strategy.md`](docs/llm-cache-strategy.md) |
| AI 작업 지침 | [`CLAUDE.md`](CLAUDE.md) |
