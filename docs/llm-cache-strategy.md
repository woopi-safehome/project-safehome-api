# LLM 캐시 전략

## 개요

AI API(`POST /api/deed/analyze`) 호출 비용 절감을 위해 분석 결과를 Redis에 캐싱한다.
동일한 등기부등본 섹션 + 임차 유형 조합이 재요청될 때 AI API 호출 없이 캐시 결과를 반환한다.

---

## 캐시 키 구조

```
llm:deed:v2:{sectionHash}:{leaseType}

예) llm:deed:v2:a3f7d9c2e1b8f4a6...:전세
    llm:deed:v2:a3f7d9c2e1b8f4a6...:월세
    llm:deed:v2:a3f7d9c2e1b8f4a6...:미지정
```

### sectionHash 계산

`AnalysisAsyncProcessor.toSha256Hash()` 참고.

1. 섹션 맵(표제부/갑구/을구)을 키 기준 알파벳 정렬
2. 각 섹션 텍스트를 `normalizeForHash()`로 정규화
   - `\r\n`, `\r` → `\n` 통일
   - 각 줄 앞뒤 공백 제거
   - 빈 줄 제거
3. `{key}:{value}` 형태로 `|` 구분자로 연결
4. SHA-256 해싱 → 64자 16진수

정규화는 **해시 계산 전에만** 적용하며, AI API로 전송하는 원본 텍스트는 변경하지 않는다.
PDFBox가 OS·버전에 따라 공백/줄바꿈을 다르게 추출하더라도 동일한 내용이면 동일한 해시가 보장된다.

### leaseType suffix

leaseType은 해시 내부에 포함하지 않고 suffix로 분리한다.
같은 등기부에 대해 임차 유형만 다른 분석 결과를 독립적으로 캐싱하기 위해서다.
값이 없으면 `"미지정"`으로 처리한다.

---

## 캐시 값

AI API 응답 JSON을 String 그대로 저장한다 (`StringRedisTemplate`).
TTL은 7일.

---

## 캐시 히트/미스 흐름

```
PDF 파싱 → DeedSections 추출
    ↓
sectionHash 계산 (정규화 후 SHA-256) + leaseType suffix
    ↓
Redis 조회
    ├── 히트 → AI API 호출 스킵, 캐시 결과 반환
    └── 미스 → AI API 호출 → Redis 저장 → 결과 반환
```

Redis 연결 실패 시 경고 로그만 출력하고 AI API 호출로 폴백한다 (서비스 중단 없음).

---

## 캐시 무효화

별도의 자동 무효화 메커니즘은 두지 않는다.

### 이유

- **등기부등본의 고유성**: 부동산마다 고유한 문서이므로 캐시 히트율이 본질적으로 낮다. 정교한 무효화보다 단순한 구조가 낫다.
- **AI API 버전 연동의 과설계 문제**: AI API 배포 시 자동으로 캐시를 무효화하는 구조를 검토했으나 아래 이유로 채택하지 않았다.
  - 모든 배포(버그픽스, 의존성 업데이트 등)가 캐시를 날려버려 무효화 범위가 지나치게 넓다.
  - "AI API 배포 → Spring Boot 캐시 무효화"라는 암묵적 결합이 생겨 운영 예측성이 떨어진다.
  - 독립 배포 구조에서 폴링 방식은 책임 소재가 뒤섞인다.

### 수동 무효화 방법

AI API의 분석 로직(프롬프트, RAG 데이터, 모델)이 바뀌어 기존 캐시를 전부 무효화해야 할 때는
`LlmCacheAdapter`의 `KEY_PREFIX` 버전을 올린다 (`v2` → `v3`).

```kotlin
// LlmCacheAdapter.kt
private const val KEY_PREFIX = "llm:deed:v3:"  // 버전 업
```

기존 `v2:*` 키는 TTL 7일이 지나면 자연 소멸한다.

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `adapter/outbound/LlmCacheAdapter.kt` | Redis 캐시 조회/저장, 키 prefix·TTL 관리 |
| `application/port/outbound/LlmCachePort.kt` | 캐시 포트 인터페이스 |
| `application/service/AnalysisAsyncProcessor.kt` | 해시 계산(`toSha256Hash`, `normalizeForHash`), 캐시 조회/저장 오케스트레이션 |
| `global/config/RedisConfig.kt` | RedisTemplate 빈 설정 |
| `global/config/EmbeddedRedisConfig.kt` | local/test 프로필용 내장 Redis |
| `resources/application-redis.yml` | 환경별 Redis 접속 설정 |
