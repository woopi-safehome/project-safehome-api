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

로컬은 환경 변수 없이 그대로 뜬다. **API 문서와 DB 콘솔은 운영에서 꺼져 있다.**
어느 프로파일에서 무엇이 켜지고 어디로 접속하는지는 각 설정 파일이 갖는다.
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

소스는 **도메인**과 **횡단 관심사** 둘로 나뉜다. 도메인 목록은 도메인 패키지의 하위 폴더가 답한다.

- **도메인** — 각각이 독립된 헥사곤이다. 표준 구조와 레이어 책임은
  **[`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md)가 기준이다** — 기존 코드가 아니라 그 문서가.
- **횡단 관심사** — 도메인과 무관하게 공유되는 공통 인프라 → [`global/README.md`](src/main/kotlin/com/woopi/safehome/global/README.md)

**밑줄로 시작하는 도메인은 참조 구현이다.** 서비스 기능이 아니라 새 도메인을 만들 때 베껴 쓰라고 둔 것이라,
지우기 전에 확인이 필요하다 — [`CLAUDE.md`](CLAUDE.md) 참조.

---

## App→API 계약

> ⚠️ **이 절은 계약이다.** 다른 절과 달리 값이 정확해야 하며, 클라이언트가 여기에 맞춘다.
> 형식을 바꾸면 이 절을 **같은 커밋에서** 갱신하고, 배포된 클라이언트가 깨지지 않는지 확인한다.
> 필드 추가는 안전하지만 **제거·개명은 전파를 먼저 계획**한다.

### 공통 규약

**인증** — 보호된 엔드포인트는 `Authorization: Bearer <accessToken>` 헤더를 요구한다.

**시각** — 시각 필드는 전부 **오프셋 없는 ISO-8601 로컬 시각**이다 (`2026-09-08T14:23:45.123`).
어느 시간대인지가 값에 들어 있지 않으므로, **UTC로 가정해 파싱하면 서버 시간대만큼 어긋난다.**

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

## 데이터베이스

테이블 목록은 초기화 SQL이 답한다. 서비스 테이블 외에 **참조 구현용 테이블도 함께 들어 있다.**

**자동 DDL을 쓰지 않는다.** 엔티티를 바꿔도 테이블은 따라오지 않으므로 스키마 파일을 함께 고쳐야 하고,
**그 파일은 두 벌이다.** 적용 방식과 초기화 SQL의 구성 → [`resources/README.md`](src/main/resources/README.md)

---

## 설정

프로파일 구분, 환경 변수, 초기화 SQL의 구성 → **[`resources/README.md`](src/main/resources/README.md)**

로컬 프로파일은 환경 변수 없이 그대로 뜬다. 소셜 로그인을 쓸 때만 키가 하나 필요하다.

---

## 배포

기본 브랜치에 push하면 이미지를 빌드해 레지스트리에 올리고, 원격 서버에서 API 컨테이너만 교체한다.
헬스 체크를 통과해야 완료로 본다. 서버 경로·비밀값 이름은 **배포 워크플로가 원본**이므로 여기 복제하지 않는다.

**롤백은 이전 태그로 되돌려 다시 띄운다.** 레지스트리 용량 한도 때문에 **직전 하나만 보관**하므로,
두 단계 이상 되돌릴 수는 없다. 문제가 의심되면 빨리 판단해야 한다.

**DB 컨테이너는 워크플로가 건드리지 않는다.** 최초 한 번 수동으로 띄우고 이후 유지된다.
API만 교체되므로 스키마 변경은 배포와 별개로 챙겨야 한다.

**운영 백업은 스케줄로 돌고 보관 기간이 설정에 있다.** 기간을 넘긴 백업은 지워진다.

---

## 결정 이유

저장소 전체에 걸친 선택과 그 근거. 개별 규칙과 함정은 **문서 지도**가 가리키는 문서들이 갖는다.

**관계형 DB를 골랐다.** 분석 결과를 JSON으로 다루는 구조라 JSON 인덱싱이 필요했고,
로컬 개발용 DB와 문법 차이가 적어 이관 부담이 낮으며, 라이선스 종속성이 없다.

**보안 프레임워크를 도입하지 않았다.** 권한 체계가 없어 직접 해석이 더 단순하다고 판단했다.
역할·권한이 생기는 시점이 이 결정을 다시 볼 때다. 대가는 [`global/README.md`](src/main/kotlin/com/woopi/safehome/global/README.md) 참조.

**마이그레이션 도구를 쓰지 않는다.** 초기화 SQL로 스키마를 적용한다.
스키마가 복잡해지면 이 결정을 다시 봐야 한다.

---

## 테스트

```bash
./gradlew test
```
Kotest `BehaviorSpec` (Given/When/Then) + JUnit 5 Platform.

---

## 문서 지도

저장소 전체에 걸친 패턴은 모듈 문서가 갖는다. 여기서 반복하지 않는다.

| 알고 싶은 것 | 문서 |
|---|---|
| 도메인 표준 구조·레이어 책임·도메인 간 의존 | [`domain/README.md`](src/main/kotlin/com/woopi/safehome/domain/README.md) |
| 인증이 성립하는 방식·탈퇴 순서 | [`domain/auth/README.md`](src/main/kotlin/com/woopi/safehome/domain/auth/README.md) |
| 비동기 분석 흐름·실패를 어디까지 실패로 보는가 | [`domain/deed/README.md`](src/main/kotlin/com/woopi/safehome/domain/deed/README.md) |
| 횡단 관심사의 범위와 조용히 깨지는 지점들 | [`global/README.md`](src/main/kotlin/com/woopi/safehome/global/README.md) |
| 설정을 나누는 원칙과 프로파일 구성 | [`resources/README.md`](src/main/resources/README.md) |
| 분석 결과 캐시의 키 구성과 무효화 정책, 그 이유 | [`docs/llm-cache-strategy.md`](docs/llm-cache-strategy.md) |
| AI 작업 지침 | [`CLAUDE.md`](CLAUDE.md) |
