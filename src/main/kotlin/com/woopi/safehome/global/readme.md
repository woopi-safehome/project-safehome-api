# global — 횡단 관심사 (Cross-Cutting Concerns)

도메인과 무관하게 앱 전체에서 공유되는 공통 인프라 계층.
도메인 패키지(`domain/`)는 이 계층에 의존하지만, 역방향 의존은 금지한다.

## 패키지 구조

```
global/
├── aop/
│   └── DataSourceTransactionInterceptor.kt  # @Transactional → Read/Write DS 분기
├── auth/
│   ├── CurrentUser.kt                        # 현재 인증 유저 추출 어노테이션
│   └── CurrentUserArgumentResolver.kt        # @CurrentUser → SecurityContext 바인딩
├── config/
│   ├── AsyncConfig.kt                        # @Async 스레드풀 설정 (analysisTaskExecutor: core 4 / max 8 / queue 50)
│   ├── EmbeddedRedisConfig.kt               # 로컬용 임베디드 Redis
│   ├── JpaAuditConfig.kt                    # createdAt / updatedAt 자동 감사
│   ├── RedisConfig.kt                       # RedisTemplate, 직렬화 설정
│   ├── RoutingDataSourceConfig.kt           # Read/Write DataSource 라우팅 빈 등록
│   ├── WebSocketConfig.kt                   # SSE/WebSocket CORS 허용 오리진
│   └── impl/
│       └── AuditAwareImpl.kt               # JpaAuditingAware → 현재 유저 ID 반환
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
│   └── BaseEntity.kt                        # createdAt, updatedAt (모든 Entity 상속)
└── response/
    ├── ApiResponse.kt                        # 통일된 API 응답 래퍼
    ├── PagedResponse.kt                      # 페이지 응답 래퍼
    └── PaginationInfo.kt                     # page, size, totalElements, hasNext
```

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

`CurrentUserArgumentResolver`가 `SecurityContextHolder`에서 userId를 꺼내 주입.

### JWT

```kotlin
jwtProvider.generateAccessToken(userId)
jwtProvider.generateRefreshToken(userId)
jwtProvider.validateToken(token)      // 만료/변조 검증
jwtProvider.extractUserId(token)
```

시크릿·만료시간은 `application-auth.yml`의 `${JWT_SECRET}`, `${JWT_ACCESS_EXPIRY}` 환경변수로 주입.

## 공통 Enum

| Enum | 값 | 용도 |
|------|---|------|
| `JobStatus` | PENDING, IN_PROGRESS, COMPLETED, FAILED | 분석 작업 상태 |
| `AnalysisStep` | PDF_PARSING, LLM_ANALYSIS, POST_PROCESSING | SSE 진행 단계 |
| `SafetyLevel` | SAFE, CAUTION, DANGER | 등기부 위험도 |
