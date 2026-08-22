# global — 횡단 관심사 (Cross-Cutting Concerns)

도메인과 무관하게 앱 전체에서 공유되는 공통 인프라 계층.
도메인 패키지(`domain/`)는 이 계층에 의존하지만, 역방향 의존은 금지한다.

> **범위**: `src/main/kotlin/com/woopi/safehome/global/**`
> **상위**: [API README](../../../../../../../README.md) · **연관**: 프로파일별 설정값 → [`resources/README.md`](../../../../../resources/README.md)
> **검증**: 파일 목록은 이 디렉토리 트리와 1:1, enum 값은 `global/enums/`와 대조

## 패키지 구조

```
global/
├── aop/
│   └── DataSourceTransactionInterceptor.kt  # @Transactional → Read/Write DS 분기
├── auth/
│   ├── CurrentUser.kt                        # 파라미터에 붙이는 인증 어노테이션
│   └── CurrentUserArgumentResolver.kt        # Authorization 헤더 → JWT 검증 → userId 주입
├── config/
│   ├── AsyncConfig.kt                        # ★ 스레드풀 + CORS + async 타임아웃 + ArgumentResolver 등록
│   ├── EmbeddedRedisConfig.kt               # 임베디드 Redis (@Profile("local", "test"))
│   ├── JpaAuditConfig.kt                    # JPA Auditing 활성화
│   ├── RedisConfig.kt                       # RedisTemplate, 직렬화 설정
│   ├── RoutingDataSourceConfig.kt           # Read/Write DataSource 라우팅 빈 등록
│   ├── WebSocketConfig.kt                   # ⚠ 전체 주석 처리된 죽은 파일 (아래 참고)
│   └── impl/
│       └── AuditAwareImpl.kt               # AuditorAware → 현재 유저 ID 반환
├── datasource/
│   ├── DataSourceContextHolder.kt           # ThreadLocal로 DS 타입 보관
│   ├── DataSourceType.kt                    # enum { READ, WRITE }
│   └── RoutingDataSource.kt                 # AbstractRoutingDataSource 구현체
├── enums/
│   ├── AnalysisStep.kt                      # PDF_PARSING / LLM_ANALYSIS / POST_PROCESSING
│   ├── JobStatus.kt                         # PENDING / IN_PROGRESS / COMPLETED / FAILED
│   └── SafetyLevel.kt                       # SAFE / CAUTION / DANGER
├── exception/
│   ├── BusinessException.kt                 # 도메인 예외 최상위 클래스
│   ├── ErrorCode.kt                         # 에러 코드 enum (HTTP status + code + message)
│   └── handler/
│       └── GlobalExceptionHandler.kt        # @RestControllerAdvice 전역 예외 핸들러
├── jwt/
│   └── JwtProvider.kt                       # JWT 생성 / 검증 / 클레임 추출
├── object/
│   └── BaseEntity.kt                        # id, isDeleted, createdId/At, updatedId/At + delete()
└── response/
    ├── ApiResponse.kt                        # 통일된 API 응답 래퍼 (sealed: Success | Error)
    ├── PagedResponse.kt                      # { items, pagination }
    └── PaginationInfo.kt                     # currentPage(1-based), totalPages, totalElements,
                                              #   size, hasNext, hasPrevious, isFirst, isLast
```

> **`WebSocketConfig.kt`는 현재 비어 있다.** 파일 전체가 주석 처리되어 있고 `application-websocket.yml`을
> 읽는 코드도 없다. **CORS는 `AsyncConfig.addCorsMappings()`가 담당한다** — CORS를 고치려면 그쪽을 본다.

## 핵심 패턴

### Read/Write DataSource 분기

```
@Transactional(readOnly = true)
    → DataSourceTransactionInterceptor
    → DataSourceContextHolder.set(READ)
    → RoutingDataSource → READ DataSource (replica)

@Transactional
    → DataSourceContextHolder.set(WRITE)
    → RoutingDataSource → WRITE DataSource (primary)
```

### API 응답 포맷

```kotlin
ApiResponse.success(data)
ApiResponse.success(data, "메시지")
ApiResponse.error("ERROR_CODE", "메시지")
```

모든 응답은 `{ "type": "success"|"error", "data": ..., "message": ... }` 형태.

### 에러 처리 흐름

```
throw BusinessException(ErrorCode.NOT_FOUND)
    → GlobalExceptionHandler.handleBusinessException()
    → ApiResponse.error(errorCode, message)
```

`ErrorCode`에 HTTP status, 코드 문자열, 기본 메시지를 한 곳에서 관리.

### 현재 유저 바인딩

```kotlin
fun getUser(@CurrentUser userId: Long): ApiResponse<*>
```

**Spring Security를 쓰지 않는다.** `CurrentUserArgumentResolver`가 직접 처리한다.

```
@CurrentUser userId: Long
  → Authorization 헤더 조회        (없으면 BusinessException(UNAUTHORIZED))
  → "Bearer " 접두어 제거
  → JwtProvider.validateAccessToken(token)  (null이면 UNAUTHORIZED)
  → userId 주입
```

리졸버는 `AsyncConfig.addArgumentResolvers()`에서 등록된다.
파라미터 타입이 `Long`이 아니면 `supportsParameter`가 false를 반환해 조용히 동작하지 않으므로 주의.

### JWT

```kotlin
jwtProvider.generateAccessToken(userId): String
jwtProvider.generateRefreshToken(userId): String
jwtProvider.validateAccessToken(token): Long?    // 검증 성공 시 userId, 실패 시 null
jwtProvider.validateRefreshToken(token): Long?
```

- 토큰에 `type` 클레임(`access` / `refresh`)을 넣어 서로 교차 사용을 막는다. 액세스 토큰을 `validateRefreshToken`에 넘기면 `null`이 나온다.
- 검증 실패는 예외가 아니라 **`null` 반환**이다 (`runCatching`으로 내부 흡수). 호출측이 분기해야 한다.
- 설정 키는 `safehome.jwt.*` (`application-auth.yml`).

| 프로퍼티 | 값 | 주입 방식 |
|---------|-----|----------|
| `safehome.jwt.secret` | 32바이트 이상 | `${JWT_SECRET:...}` 환경변수 |
| `safehome.jwt.access-token-expiry` | `3600` (1시간, 초) | **하드코딩 — 환경변수 없음** |
| `safehome.jwt.refresh-token-expiry` | `1209600` (14일, 초) | **하드코딩 — 환경변수 없음** |

### 비동기 · CORS · 리졸버 (`AsyncConfig`)

이름과 달리 `@Async` 설정만 하는 클래스가 아니다. 웹 계층 설정이 함께 들어 있다.

| 담당 | 내용 |
|------|------|
| `analysisTaskExecutor` 빈 | core 4 / max 8 / queue 50, prefix `analysis-`, 포화 시 `CallerRunsPolicy` |
| `configureAsyncSupport` | async 요청 기본 타임아웃 300초 (SSE emitter 타임아웃과 동일) |
| `addCorsMappings` | `/api/**` — 모든 오리진 패턴, `GET POST DELETE OPTIONS` |
| `addArgumentResolvers` | `CurrentUserArgumentResolver` 등록 |

### 페이징

```kotlin
PagedResponse(items = ..., pagination = PaginationInfo.from(page))
```

> **0-based / 1-based가 섞여 있다.** 요청 파라미터 `page`는 **0-based**(Spring `PageRequest`)인데
> 응답의 `PaginationInfo.currentPage`는 **1-based**로 변환된다(`page.number + 1`).
> 클라이언트에서 응답값을 그대로 다음 요청에 넣으면 한 페이지 건너뛴다.

`ApiResponse.Success`에도 `pagination` 필드가 있지만 목록 응답은 `PagedResponse`로 감싸는 방식을 쓴다.

## 공통 Enum

| Enum | 값 | 용도 |
|------|---|------|
| `JobStatus` | PENDING, IN_PROGRESS, COMPLETED, FAILED | 분석 작업 상태 |
| `AnalysisStep` | PDF_PARSING, LLM_ANALYSIS, POST_PROCESSING | SSE 진행 단계 |
| `SafetyLevel` | SAFE, CAUTION, DANGER | 등기부 위험도 |
