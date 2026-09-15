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
bash scripts/verify.sh                                        # 검증 — CI 의 테스트 단계와 같은 파일
./gradlew bootRun --args='--spring.profiles.active=local'     # 개발 서버 (로컬 프로파일)
./gradlew build -x test                                       # 빌드 (테스트를 돌리지 않는다)
./gradlew test --tests "*ClassName"                           # 단일 클래스
python scripts/ci_status.py                                   # 푸시 뒤 원격 CI 결과
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

새 도메인을 만들 때 베껴 쓸 참조 구현은 두지 않는다. **표준 구조 문서가 기준이고, 기존 도메인은 그 적용례일 뿐이다.**
계층 규칙은 제약 테스트가 강제하므로, 구조를 어기면 테스트가 깨진다.

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

테이블 목록은 초기화 SQL이 답한다.

**자동 DDL을 쓰지 않는다.** 엔티티를 바꿔도 테이블은 따라오지 않으므로 스키마 파일을 함께 고쳐야 하고,
**그 파일은 두 벌이다.** 적용 방식과 초기화 SQL의 구성 → [`resources/README.md`](src/main/resources/README.md)

**테이블을 지우는 일은 자동화하지 않는다.** 초기화 SQL 에서 정의를 빼도 이미 떠 있는 DB 에는
그대로 남는다. 초기화 스크립트는 기동할 때마다 도므로 거기에 `DROP` 을 넣으면 안 된다.
일회성 정리는 [`docker/maintenance/`](docker/maintenance) 아래에 두고 사람이 판단해서 돌린다.

---

## 설정

프로파일 구분, 환경 변수, 초기화 SQL의 구성 → **[`resources/README.md`](src/main/resources/README.md)**

로컬 프로파일은 환경 변수 없이 그대로 뜬다. 소셜 로그인을 쓸 때만 키가 하나 필요하다.

---

## 배포

**배포되는 것은 개발 환경뿐이다.** 어느 브랜치가 방아쇠인지, 서버 경로와 비밀값 이름이
무엇인지는 **배포 워크플로가 원본**이므로 여기 복제하지 않는다.

**운영 구성에는 애플리케이션이 없다.** DB와 그 백업만 정의돼 있다.
운영 배포 경로를 만들 때 이 절과 구성 파일을 함께 봐야 한다.

**테스트를 통과해야 빌드가 시작된다.** 테스트가 깨지면 이미지가 만들어지지 않는다.

**이미지를 올리기 전에 실제로 띄워 본다.** DB와 캐시를 임시로 함께 띄운 채, 배포 구성의 헬스 체크 명령을
컨테이너 안에서 그대로 돌린다. 코드 테스트는 이미지를 보지 않기 때문이다.

**배포는 컨테이너가 건강해질 때까지 기다린다.** 띄우라고 지시하는 것으로 끝내지 않고
헬스 체크가 통과하는지 확인하며, 시간 안에 통과하지 못하면 컨테이너 로그를 남기고 실패한다.
그래서 배포가 초록이면 서비스가 응답한다는 뜻이다.

**배포는 API 컨테이너만 교체하지만, DB와 캐시 컨테이너도 함께 기동을 확인한다.**
이미 떠 있으면 그대로 두고, 내려가 있으면 올린다. 구성 파일을 바꾼 뒤 배포하면
그 컨테이너가 재생성될 수 있다는 뜻이다.

**스키마 변경은 배포와 별개로 챙긴다.** 자동 DDL도 마이그레이션 도구도 없다.

**롤백은 이전 이미지 태그로 되돌려 다시 띄운다.** 커밋마다 고유 태그가 남으므로
되돌릴 지점은 있다. 다만 서버에 남아 있는 이미지는 배포 때마다 정리되므로,
오래된 태그로 가려면 레지스트리에서 다시 받아야 한다.

**백업 스크립트는 저장소에 있지만, 그것을 주기적으로 실행하는 것은 저장소에 없다.**
스크립트는 덤프를 남기고 보관 기간이 지난 것을 지우며, 기간은 환경 변수로 조절된다.
**정말 돌고 있는지는 서버에서 확인해야 한다** — 여기서는 알 수 없다.

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
bash scripts/verify.sh
```
**CI 의 테스트 단계가 이 파일을 그대로 실행한다.** 명령이 다르면 로컬 통과가 CI 통과를 뜻하지 않는다.
Given/When/Then 형식을 쓴다. 형식과 도구는 기존 테스트를 보고 맞춘다.

**제약 테스트**는 기능이 아니라 이 코드베이스가 유지해야 할 성질을 검사한다.
계층 의존 방향, 엔티티 위치, 인증 인자, 나가는 HTTP 클라이언트의 생성 지점,
컨트롤러의 응답 형태, 유스케이스의 트랜잭션 기본값 — 전부 어겨도 컴파일과 실행이
정상이라 조용히 지나가던 것들이다. 어기면 실패 메시지가 이유와 근거 문서를 함께 알려준다.

**문서도 제약 테스트가 본다.** 링크와 인용한 절이 실제로 있는지, 계약 절의 표식, 머리글과 문서 지도,
제약 테스트가 실패 메시지에 근거 문서를 적었는지. **문장이 코드 동작에 대해 사실인지는 보지 못한다.**

**어댑터 테스트**는 대역에 가려진 자리를 본다. 유스케이스 테스트는 바깥으로 나가는 길을
전부 대역으로 바꾸므로, 그 구현이 망가져도 거기서는 드러나지 않는다.
바깥으로 나가는 호출은 HTTP 응답을 흉내내 검사하고, 들어오는 요청은 요청을 명령으로
옮기는 과정만 검사한다 — 인증은 다른 자리에서 본다.

**실행 테스트**는 코드만 읽어서는 판단할 수 없는 동작을 실제로 돌려 본다. 읽기 전용 트랜잭션이
어느 인스턴스를 쓰는지가 그렇다 — 설정과 인터셉터가 있어도 분리는 동작하지 않았다.

푸시 뒤에는 `python scripts/ci_status.py` 로 원격 결과를 본다. 로컬 통과는 중간 확인이다.

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
