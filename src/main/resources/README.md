# resources — 환경설정 프로파일

Spring Boot 설정 파일 모음. `application.yml`이 루트이며 나머지는 기능별로 분리된 프로파일 파일이다.

## 파일 목록

| 파일 | 역할 |
|------|------|
| `application.yml` | 루트 설정. 모든 하위 프로파일을 `import`로 로드 |
| `application-auth.yml` | JWT 시크릿·만료시간, 카카오 Admin Key |
| `application-db.yml` | DataSource (local: H2, dev/prd: PostgreSQL Read/Write 분리) |
| `application-redis.yml` | Redis host (local: localhost, dev/prd: 환경변수) |
| `application-ai.yml` | AI API 연동 URL |
| `application-sentry.yml` | Sentry DSN, traces-sample-rate, 환경 태그 |
| `application-swagger.yml` | Swagger UI 경로, local/dev 활성, prd 비활성 |
| `application-logging.yml` | 로그 레벨 (local/dev: DEBUG, prd: ERROR) |
| `application-websocket.yml` | SSE/WebSocket CORS 허용 오리진 |

## 프로파일 구분

| 프로파일 | 실행 환경 | DB | 비고 |
|---------|---------|-----|------|
| `local` | 개발자 로컬 | H2 (파일) | H2 Console 활성, Embedded Redis |
| `dev` | 개발 서버 | PostgreSQL (Primary + Replica) | Swagger 활성 |
| `prd` | 운영 서버 | PostgreSQL (Primary + Replica) | Swagger 비활성, 로그 ERROR만 |

프로파일 활성화:
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
# 또는 환경변수
SPRING_PROFILES_ACTIVE=dev
```

## 환경변수 목록

실제 값은 코드에 절대 하드코딩하지 않는다. 모두 `${VAR:기본값}` 형태로 주입.

| 변수 | 관련 파일 | 필수 | 설명 |
|------|---------|:---:|------|
| `JWT_SECRET` | application-auth.yml | dev/prd | JWT 서명 시크릿 (32바이트 이상) |
| `KAKAO_ADMIN_KEY` | application-auth.yml | ✅ | 카카오 Admin Key (회원탈퇴 등) |
| `DB_WRITE_HOST` | application-db.yml | dev/prd | PostgreSQL Primary 호스트 |
| `DB_READ_HOST` | application-db.yml | dev/prd | PostgreSQL Replica 호스트 |
| `DB_WRITE_PORT` | application-db.yml | dev/prd | Primary 포트 (기본 5432) |
| `DB_READ_PORT` | application-db.yml | dev/prd | Replica 포트 (기본 5433) |
| `DB_NAME` | application-db.yml | dev/prd | DB 이름 (기본 safehome) |
| `DB_USERNAME` | application-db.yml | dev/prd | DB 유저 (기본 safehome) |
| `DB_PASSWORD` | application-db.yml | ✅ | DB 패스워드 |
| `SQL_INIT_MODE` | application-db.yml | - | schema/data 초기화 모드 (기본 always) |
| `REDIS_HOST` | application-redis.yml | dev/prd | Redis 호스트 (기본 localhost) |
| `AI_API_URL` | application-ai.yml | - | AI API 주소 (기본 http://localhost:5000) |
| `SENTRY_DSN` | application-sentry.yml | - | Sentry DSN (미설정 시 Sentry 비활성) |
| `SPRING_PROFILES_ACTIVE` | application-sentry.yml | - | Sentry environment 태그로 사용 |

## local 환경 최소 설정

`local` 프로파일은 H2 + Embedded Redis를 사용하므로 환경변수 없이 실행 가능.
단, 카카오 로그인 기능 사용 시 `KAKAO_ADMIN_KEY` 필요.

```bash
./gradlew bootRun
# H2 Console: http://localhost:8080/h2-console
# Swagger:    http://localhost:8080/swagger-ui.html
```

## 서버 환경 설정 위치

dev/prd 서버에서는 GitHub Actions가 아닌 **서버 로컬 env 파일**로 관리:
```
/home/woopi/project/safehome/env/.env_api
```
Docker Compose에서 `--env-file` 옵션으로 로드.
