# auth 도메인

카카오 소셜 로그인, JWT 토큰 발급/갱신, 회원 탈퇴를 처리하는 인증 도메인.
Spring Security 없이 `@CurrentUser` 커스텀 애노테이션과 `HandlerMethodArgumentResolver`로 인증을 처리한다.

---

## 패키지 구조

```
auth/
├── adapter/
│   ├── inbound/web/
│   │   ├── AuthInboundWebAdapter         # POST /api/auth/kakao, POST /api/auth/refresh
│   │   ├── UserInboundWebAdapter         # DELETE /api/users/me, POST /api/users/devices
│   │   └── dto/
│   │       ├── AuthRequest               # 요청 DTO (KakaoLogin, Refresh)
│   │       ├── AuthResponse              # 응답 DTO (Login, Token)
│   │       └── DeviceRequest             # FCM 디바이스 토큰 요청 DTO (Register)
│   └── outbound/
│       ├── KakaoApiAdapter               # KakaoApiPort 구현체 (Kakao REST API 호출)
│       └── persistence/
│           ├── UserEntityMapper          # UserEntity ↔ User.Data 변환
│           ├── UserPersistenceAdapter    # UserPersistencePort 구현체 (JPA)
│           ├── UserDevicePersistenceAdapter  # UserDevicePersistencePort 구현체 (JPA upsert)
│           └── jpa/
│               ├── UserEntity            # JPA 엔티티 (BaseEntity 상속)
│               ├── UserRepository        # Spring Data JPA Repository
│               ├── UserDeviceEntity      # FCM 디바이스 JPA 엔티티 (userId, fcmToken, unique)
│               └── UserDeviceRepository  # Spring Data JPA Repository (findAllFcmTokenByUserId)
│
├── application/
│   ├── port/
│   │   ├── inbound/
│   │   │   └── AuthUseCase               # 카카오 로그인, 토큰 갱신, 회원 탈퇴, 디바이스 등록 인터페이스
│   │   └── outbound/
│   │       ├── UserPersistencePort       # 사용자 조회/저장/삭제 포트
│   │       ├── KakaoApiPort              # 카카오 사용자 정보 조회, 연결 해제 포트
│   │       └── UserDevicePersistencePort # FCM 토큰 upsert/삭제 포트
│   └── usecase/
│       └── AuthUseCaseImpl               # 비즈니스 흐름 구현
│
└── domain/
    └── model/
        └── User.kt                       # User.Create / User.Data, KakaoUserInfo, AuthResult, TokenPair
```

---

## 요청 흐름

```
POST /api/auth/kakao
  AuthInboundWebAdapter
    → AuthUseCase.kakaoLogin(kakaoAccessToken)
      → AuthUseCaseImpl
          1. KakaoApiPort.getUserInfo(token)      # Kakao API로 사용자 정보 조회
          2. UserPersistencePort.findByKakaoId()  # 기존 회원 조회
          3. 신규 회원이면 UserPersistencePort.save()
          4. JwtProvider.generateAccessToken/RefreshToken(userId)
          5. return AuthResult(tokens, isNewUser)

POST /api/auth/refresh
  AuthInboundWebAdapter
    → AuthUseCase.refresh(refreshToken)
      → JwtProvider.validateRefreshToken()       # 유효성 검증 + userId 추출
      → JwtProvider.generate*Token(userId)       # 새 토큰 쌍 발급
      → return TokenPair

DELETE /api/users/me   [Authorization: Bearer {accessToken}]
  UserInboundWebAdapter (@CurrentUser → userId 추출)
    → AuthUseCase.withdraw(userId)
      → UserPersistencePort.findById()           # 사용자 조회 (kakaoId 확보)
      → KakaoApiPort.unlinkUser(kakaoId)         # 카카오 연결 해제
      → UserPersistencePort.deleteById()         # 소프트 딜리트

POST /api/users/devices   [Authorization: Bearer {accessToken}]
  UserInboundWebAdapter (@CurrentUser → userId 추출)
    → AuthUseCase.registerDevice(userId, fcmToken)
      → UserDevicePersistencePort.upsert(userId, fcmToken)
          # fcmToken unique 제약: 동일 토큰 재등록 시 update, 신규 시 insert
```

---

## 클래스 역할

### adapter/inbound

| 클래스 | 역할 |
|--------|------|
| `AuthInboundWebAdapter` | `POST /api/auth/kakao` (카카오 로그인), `POST /api/auth/refresh` (토큰 갱신) |
| `UserInboundWebAdapter` | `DELETE /api/users/me` (회원 탈퇴), `POST /api/users/devices` (FCM 토큰 등록). 모두 `@CurrentUser` 인증 필요 |
| `AuthRequest` | 요청 DTO. `KakaoLogin(kakaoAccessToken)`, `Refresh(refreshToken)` |
| `AuthResponse` | 응답 DTO. `Login(tokens + isNewUser)`, `Token(tokens)` |
| `DeviceRequest` | FCM 디바이스 요청 DTO. `Register(fcmToken)` |

### adapter/outbound

| 클래스 | 역할 |
|--------|------|
| `KakaoApiAdapter` | `RestTemplate`으로 Kakao API 호출. `getUserInfo` (Bearer token), `unlinkUser` (KakaoAK admin key) |
| `UserPersistenceAdapter` | `UserRepository`를 통해 사용자 조회/저장/소프트 딜리트 |
| `UserDevicePersistenceAdapter` | `UserDeviceRepository`를 통해 FCM 토큰 upsert(저장 또는 갱신) / userId 기준 전체 삭제 |
| `UserEntity` | `BaseEntity` 상속 JPA 엔티티 (kakaoId, nickname, profileImageUrl) |
| `UserEntityMapper` | `UserEntity` ↔ `User.Data` 변환 |
| `UserRepository` | Spring Data JPA Repository (`findByKakaoIdAndIsDeletedFalse`, `findByIdAndIsDeletedFalse`) |
| `UserDeviceEntity` | `BaseEntity` 상속 JPA 엔티티 (userId, fcmToken unique). 테이블: `user_devices` |
| `UserDeviceRepository` | Spring Data JPA Repository (`findAllFcmTokenByUserId`, `deleteByUserId`) |

### application/port/inbound

| 인터페이스 | 역할 |
|-----------|------|
| `AuthUseCase` | `kakaoLogin(token): AuthResult`, `refresh(token): TokenPair`, `withdraw(userId)`, `registerDevice(userId, fcmToken)` |

### application/port/outbound

| 인터페이스 | 역할 |
|-----------|------|
| `UserPersistencePort` | `findByKakaoId`, `findById`, `save`, `deleteById` |
| `KakaoApiPort` | `getUserInfo(accessToken): KakaoUserInfo`, `unlinkUser(kakaoId)` |
| `UserDevicePersistencePort` | `upsert(userId, fcmToken)` (토큰 저장/갱신), `deleteByUserId(userId)` (회원 탈퇴 시 전체 삭제). deed 도메인의 `UserDeviceQueryAdapter`가 `UserDeviceRepository`를 직접 참조 |

### application/usecase

| 클래스 | 역할 |
|--------|------|
| `AuthUseCaseImpl` | 카카오 로그인(신규/기존 분기), 토큰 갱신, 회원 탈퇴 비즈니스 흐름 조율 |

### domain/model

| 클래스 | 역할 |
|--------|------|
| `User.Create` | 사용자 생성 입력 모델 (kakaoId, nickname, profileImageUrl) |
| `User.Data` | 사용자 조회 결과 모델 (id 포함) |
| `KakaoUserInfo` | 카카오 API 응답 파싱 결과 |
| `AuthResult` | 로그인 응답 (accessToken, refreshToken, expiresIn, isNewUser) |
| `TokenPair` | 토큰 갱신 응답 (accessToken, refreshToken, expiresIn) |
