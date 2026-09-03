# SafeHome API

등기부등본 분석 서비스의 **메인 백엔드**. 인증(카카오+JWT), PDF 파싱, 분석 Job 관리, LLM 응답 캐싱, SSE 실시간 알림, 푸시 발송 트리거를 담당한다.
AI 분석 자체는 하지 않는다 — AI API에 위임한다.

> **범위**: `project-safehome-api/**`
> **연관**: [AI API README](../project-safehome-ai-api/README.md) — API→AI API 계약의 원본 (워크스페이스에 함께 있을 때만 유효한 링크)
> **여기 없는 것**: 클래스 이름과 라이브러리 버전 — 코드와 `build.gradle.kts`가 답한다.
> 아래 계약 절은 웹 어댑터·설정 파일과 대조해 확인한다.

---

## TL;DR

인증, 문서 파싱, 분석 작업 관리, 결과 캐싱, 진행 상황 실시간 알림, 푸시 발송을 담당한다.
**AI 분석 자체는 하지 않는다** — 외부 분석 서버에 위임하고 결과를 통과시킨다.

헥사고날 아키텍처. 도메인마다 독립된 헥사곤을 이루며, 구조는
[`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md)가 기준이다.

```bash
./gradlew bootRun                     # 개발 서버 (로컬 프로파일)
./gradlew build -x test               # 빌드
./gradlew test                        # 전체 테스트
./gradlew test --tests "*ClassName"   # 단일 클래스
```

로컬은 환경 변수 없이 그대로 뜬다. API 문서와 DB 콘솔 주소는 아래 **설정** 절 참조.
스택과 라이브러리 버전은 `build.gradle.kts`가 답한다.

---

## 작업 레시피

파일 이름이 아니라 **순서**가 중요하다. 실제 위치는 [`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md)의 표준 구조를 따른다.

| 하려는 일 | 순서 |
|-----------|------|
| **새 엔드포인트 추가** | 인바운드 포트 → 유스케이스 → 웹 어댑터 → 요청·응답 형식 → 도메인 README |
| **새 외부 시스템 연동** | 아웃바운드 포트(**인터페이스 먼저**) → 어댑터 → 설정에 주소 추가 → [`resources/README.md`](src/main/resources/README.md) |
| **새 도메인 추가** | [`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md)의 표준 구조를 따르고, 도메인 README를 함께 만든다 |
| **분석 단계 추가·변경** | 단계 열거값 → 비동기 처리부 → 아래 **App→API 계약** 절 → 앱의 같은 열거값 |
| **DB 컬럼 추가** | **스키마 파일 양쪽 모두** → 엔티티 → 매퍼 → 도메인 모델 |
| **에러 코드 추가** | 에러 코드 열거값 → 아래 **App→API 계약** 절 |
| **캐시 동작 변경** | 캐시 어댑터 → [`docs/llm-cache-strategy.md`](docs/llm-cache-strategy.md) |

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

## App→API 계약

> ⚠️ **이 절은 계약이다.** 다른 절과 달리 값이 정확해야 하며, 클라이언트가 여기에 맞춘다.
> 형식을 바꾸면 이 절을 **같은 커밋에서** 갱신하고, 배포된 클라이언트가 깨지지 않는지 확인한다.
> 필드 추가는 안전하지만 **제거·개명은 전파를 먼저 계획**한다.

### 공통 규약

**인증** — 보호된 엔드포인트는 `Authorization: Bearer <accessToken>` 헤더를 요구한다.
Spring Security를 쓰지 않고 인자 리졸버가 헤더를 직접 해석한다.

**응답 봉투** — 모든 JSON 응답은 아래 형태로 감싸인다. `type` 이 판별 필드다.

```jsonc
// 성공
{ "type": "success", "data": { ... }, "message": "Success", "pagination": null }

// 실패
{ "type": "error", "code": "NOT_FOUND", "message": "...", "details": null }
```

**페이징** — 목록 응답의 `data` 는 `{ items: [...], pagination: {...} }` 이고,
`pagination` 은 `currentPage`(1부터) · `totalPages` · `totalElements` · `size` ·
`hasNext` · `hasPrevious` · `isFirst` · `isLast` 를 갖는다.

**에러 코드** — `code` 필드에 들어가는 값과 HTTP 상태:

| code | HTTP |
|---|---|
| `VALIDATION_FAILED` · `CONSTRAINT_VIOLATION` | 400 |
| `UNAUTHORIZED` | 401 |
| `FORBIDDEN` | 403 |
| `NOT_FOUND` | 404 |
| `KAKAO_API_ERROR` | 502 |
| `INTERNAL_SERVER_ERROR` | 500 |

**공유 열거값** — 클라이언트도 같은 값을 쓴다. 문자열 하드코딩 금지.

| 열거 | 값 |
|---|---|
| 분석 상태 | `PENDING` · `IN_PROGRESS` · `COMPLETED` · `FAILED` |
| 분석 단계 | `PDF_PARSING` · `LLM_ANALYSIS` · `POST_PROCESSING` |
| 안전 등급 | `SAFE` · `CAUTION` · `DANGER` |

### 인증

| | |
|---|---|
| `POST /api/auth/kakao` | 카카오 로그인 / 회원가입 |
| 요청 | `{ "kakaoAccessToken": string }` |
| 응답 `data` | `{ "accessToken": string, "refreshToken": string, "expiresIn": number, "isNewUser": boolean }` |

| | |
|---|---|
| `POST /api/auth/refresh` | 액세스 토큰 재발급 |
| 요청 | `{ "refreshToken": string }` |
| 응답 `data` | `{ "accessToken": string, "refreshToken": string, "expiresIn": number }` |

### 사용자 🔒

| | |
|---|---|
| `POST /api/users/devices` | FCM 디바이스 토큰 등록 (동일 토큰 재등록은 upsert) |
| 요청 | `{ "fcmToken": string }` — 빈 문자열 불가 |
| 응답 `data` | 없음 |

| | |
|---|---|
| `DELETE /api/users/me` | 회원 탈퇴 (카카오 연결 해제 포함) |
| 응답 `data` | 없음 |

### 등기부등본 분석 🔒

| | |
|---|---|
| `POST /api/deed/upload` | PDF 업로드 → 분석 Job 생성 |
| 요청 | `multipart/form-data` — `file` (PDF, 필수) · `leaseType` (`전세` \| `월세`, 선택) |
| 응답 `data` | `{ "jobId": string }` |

| | |
|---|---|
| `GET /api/deed/jobs/{jobId}/stream` | 분석 진행 상황 구독 (SSE) |
| 응답 | `text/event-stream`. **봉투로 감싸지 않는다** — 아래 이벤트 페이로드를 그대로 보낸다 |
| 이벤트 | `{ "jobId": string, "status": 분석상태, "step": 분석단계 \| null, "message": string, "timestamp": ISO-8601 }` |
| 종료 | `status` 가 `COMPLETED` 또는 `FAILED` 이면 서버가 스트림을 닫는다 |

| | |
|---|---|
| `GET /api/deed/jobs/{jobId}` | Job 단건 조회 |
| 응답 `data` | `{ "jobId": string, "fileName": string, "fileSize": number, "status": 분석상태, "step": 분석단계 \| null, "description": string \| null, "result": object \| null }` |

> `result` 는 **JSON 객체를 그대로** 내려준다(문자열로 감싸지 않음). 분석이 끝나기 전에는 `null`.
> 그 내부 구조는 AI 분석 서버가 정하는 계약이며, 이 서버는 통과시키기만 한다.

| | |
|---|---|
| `GET /api/deed/jobs` | 내 분석 이력 목록 |
| 쿼리 | `page` (0부터, 기본 0) · `size` (기본 20) |
| 응답 `data` | `{ "items": [ ... ], "pagination": { ... } }` |
| `items[]` | `{ "jobId": string, "fileName": string, "fileSize": number, "status": 분석상태, "safetyLevel": 안전등급 \| null, "address": string \| null, "createdAt": ISO-8601 \| null, "leaseType": string \| null }` |

> 요청의 `page` 는 0부터, 응답 `pagination.currentPage` 는 1부터다. 서로 기준이 다르다.

---

## 핵심 패턴

### 응답과 예외

성공은 공통 봉투로 감싸고, 실패는 **정해진 예외를 던지면** 전역 핸들러가 봉투로 변환한다.
HTTP 상태·에러 코드·기본 메시지는 열거값 한 곳에서 관리하므로, 컨트롤러가 상태 코드를 직접 정하지 않는다.

### 읽기/쓰기 데이터소스 분기

**트랜잭션 애노테이션의 읽기 여부가 커넥션을 결정한다.** 읽기 전용이면 복제본으로, 아니면 주 인스턴스로 간다.
컨텍스트를 직접 조작하지 말고 애노테이션만 정확히 붙인다 — 빠뜨려도 동작은 정상이라 드러나지 않는다.

상세 → [`global/README.md`](src/main/kotlin/com/woopi/safehome/global/README.md)

### 비동기 분석

Job을 저장하고 **트랜잭션이 커밋된 뒤에** 비동기 작업을 띄운다. 전용 스레드풀을 쓴다.

> **순서를 바꾸면 안 된다.** 커밋 전에 띄우면 비동기 스레드가 **아직 존재하지 않는 Job을 조회**한다.
> 타이밍에 따라 되기도 하고 안 되기도 해서 재현이 어렵다.

---

## 데이터베이스

| 테이블 | 용도 |
|--------|------|
| `analysis_jobs` | 분석 작업과 그 결과 |
| `users` | 회원 (탈퇴는 소프트 딜리트) |
| `user_devices` | 푸시 토큰 (재등록은 upsert) |

- **자동 DDL을 쓰지 않는다.** 스키마는 초기화 SQL로만 적용되므로 엔티티를 바꿔도 테이블은 따라오지 않는다.
- **스키마 파일이 두 벌이다** — 로컬용과 서버용. **한쪽만 고치면 로컬은 되고 서버에서 깨진다.**
- 모든 테이블이 공통 감사 컬럼(생성·수정 주체와 시각)을 갖는다. 상속 기반 엔티티를 쓰면 자동으로 채워진다.

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

기본 브랜치에 push하면 이미지를 빌드해 레지스트리에 올리고, 원격 서버에서 API 컨테이너만 교체한다.
헬스 체크를 통과해야 완료로 본다. 서버 경로·비밀값 이름은 **배포 워크플로가 원본**이므로 여기 복제하지 않는다.

**롤백은 이전 태그로 되돌려 다시 띄운다.** 레지스트리 용량 한도 때문에 **직전 하나만 보관**하므로,
두 단계 이상 되돌릴 수는 없다. 문제가 의심되면 빨리 판단해야 한다.

**DB 컨테이너는 워크플로가 건드리지 않는다.** 최초 한 번 수동으로 띄우고 이후 유지된다.
API만 교체되므로 스키마 변경은 배포와 별개로 챙겨야 한다.

**운영 백업은 스케줄로 돌고 보관 기간이 설정에 있다.** 기간을 넘긴 백업은 지워진다.

> **관계형 DB 선택 이유**: 분석 결과를 JSON으로 다루는 구조라 JSON 인덱싱이 필요했고,
> 로컬 개발용 DB와 문법 차이가 적어 이관 부담이 낮으며, 라이선스 종속성이 없다.

---

## 함정 & 결정 이유

| 함정 | 내용 |
|------|------|
| **프로파일 이름 불일치** | 설정 파일 하나만 운영 프로파일 이름을 다르게 적고 있다. 그래서 운영으로 띄우면 그 파일의 설정이 적용되지 않는다. **에러 없이 의도와 다른 상태로 뜬다** |
| **스키마 파일 두 벌** | 로컬용과 서버용을 따로 관리한다. 한쪽만 고치면 로컬은 되고 서버에서 깨진다 |
| **분석 결과는 JSON 원본으로 나간다** | 문자열 필드지만 응답에서는 객체로 내려간다. 클라이언트가 이중 파싱해야 할 수 있다 |
| **스트리밍 연결 끊김은 정상** | 클라이언트가 먼저 끊는 경우가 있다. 이때 발생하는 예외를 에러로 처리하면 로그가 오염되고 알림이 울린다 |
| **푸시 실패는 무시한다** | 결과 저장이 끝난 뒤 발송하므로, 발송 실패가 분석을 실패시키면 안 된다 |
| **캐시 장애도 무시한다** | 캐시 계층은 예외를 삼키고 미스처럼 동작한다. 그대로 원 서버 호출로 폴백한다 |
| **도메인 간 의존은 단방향** | 다른 도메인의 데이터가 필요하면 포트와 어댑터로 격리한다. 의존받는 쪽은 상대를 모른다 |
| **캐시 개별 무효화가 없다** | 입력이 문서마다 고유해 히트율이 낮아 그렇게 설계했다. 전체를 비우려면 캐시 키 버전을 올린다. 배경 → [`docs/llm-cache-strategy.md`](docs/llm-cache-strategy.md) |

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
