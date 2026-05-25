# Domain 패키지

헥사고날 아키텍처(Ports & Adapters) + DDD 기반으로 설계된 도메인 패키지.
각 도메인은 독립된 헥사곤으로, `adapter / application / domain` 3개 레이어로 구성된다.

---

## 도메인 목록

| 도메인 | 설명 |
|--------|------|
| `auth` | 카카오 소셜 로그인, JWT 토큰 발급/갱신, 회원 탈퇴 |
| `deed` | 등기부등본 PDF 분석 요청 처리 (핵심 도메인) |
| `analysisjob` | 비동기 분석 Job 실행 및 SSE 실시간 알림 |
| `_sample` | CRUD 참조 구현체 (새 도메인 작성 시 템플릿) |

---

## 도메인 패키지 보일러플레이트

```
{domain}/
├── adapter/                          # [기술 레이어] 외부 시스템과의 연결
│   ├── inbound/
│   │   └── web/                      # HTTP 진입점
│   │       ├── {Name}InboundWebAdapter
│   │       └── dto/
│   │           ├── {Name}Request
│   │           ├── {Name}Response
│   │           └── {Name}DtoMapper
│   └── outbound/
│       └── persistence/              # 영속성 (JPA)
│           ├── {Name}PersistenceAdapter
│           └── jpa/
│               ├── {Name}Entity
│               ├── {Name}EntityMapper
│               └── {Name}Repository
│
├── application/                      # [응용 레이어] 유스케이스 + 포트 정의
│   ├── port/
│   │   ├── inbound/                  # 도메인이 외부에 제공하는 기능 명세
│   │   │   └── {Name}UseCase
│   │   └── outbound/                 # 도메인이 외부에 요구하는 기능 명세
│   │       └── {Name}PersistencePort
│   └── usecase/                      # 인바운드 포트 구현체 (비즈니스 흐름 조율)
│       └── {Name}UseCaseImpl
│
└── domain/                           # [도메인 레이어] 순수 비즈니스 로직
    ├── model/                        # 도메인 모델 (JPA 엔티티와 분리된 순수 데이터 클래스)
    │   └── {Name}.kt
    └── service/                      # 도메인 서비스 (선택적, Spring 의존성 없음)
        ├── {Name}DomainService
        └── impl/
            └── Default{Name}DomainService
```

---

## 레이어 책임

### adapter (기술 레이어)
외부 세계와 도메인 사이의 변환만 담당한다. 비즈니스 로직을 포함하지 않는다.

- **inbound**: 외부 호출을 받아 application 포트로 전달 (HTTP, 메시지 등)
- **outbound**: application이 정의한 outbound 포트의 구현체 (JPA, SSE, 외부 API 등)

### application (응용 레이어)
비즈니스 흐름을 조율한다. 도메인 로직을 직접 갖지 않고 도메인 서비스와 포트를 조합한다.

- **port/inbound**: 도메인이 외부에 노출하는 유스케이스 인터페이스 (진입점 계약)
- **port/outbound**: 도메인이 필요로 하는 인프라/외부 기능의 추상화
- **usecase**: 인바운드 포트의 구현체. 여러 포트와 도메인 서비스를 조합해 흐름을 처리

### domain (도메인 레이어)
순수 비즈니스 규칙만 존재한다. Spring, JPA 등 기술 의존성 없음.

- **model**: 도메인의 핵심 데이터 구조 (JPA 엔티티와 분리, `adapter/outbound/persistence/jpa`에 별도 엔티티 존재)
- **service**: 단일 엔티티를 넘어서는 도메인 로직 (선택적으로 사용)

---

## 요청 흐름

```
[HTTP 요청]
    ↓
adapter/inbound/web/{Name}InboundWebAdapter
    ↓  (인바운드 포트 호출)
application/port/inbound/{Name}UseCase
    ↓  (구현체)
application/usecase/{Name}UseCaseImpl
    ↓  (아웃바운드 포트 호출)           ↓  (도메인 서비스 호출)
application/port/outbound/             domain/service/
    ↓  (포트 구현체)
adapter/outbound/persistence/{Name}PersistenceAdapter
```

---

## 도메인 간 의존 규칙

- 의존 방향은 단방향으로 고정한다
- 역방향 의존은 허용하지 않는다
- 역방향이 필요한 경우 의존하는 쪽에 포트를 정의하고 상대 도메인이 어댑터로 구현한다

```
deed  ──→  analysisjob
deed  ──→  auth          # UserDeviceQueryAdapter가 auth의 UserDeviceRepository를 직접 참조 (FCM 토큰 조회)
```

> `deed → auth` 의존은 포트(`UserDeviceQueryPort`) + 어댑터(`UserDeviceQueryAdapter`) 패턴으로 격리되어 있다.
> auth 도메인은 deed를 알지 못한다.
