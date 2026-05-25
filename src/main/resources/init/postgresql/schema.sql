-- 샘플 마스터 테이블
CREATE TABLE IF NOT EXISTS samples (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    code        VARCHAR(100)  NOT NULL,
    description TEXT          NULL,
    order_no    INT           NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_id  BIGINT        NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id  BIGINT        NULL,
    updated_at  TIMESTAMP     NULL,

    CONSTRAINT uk_samples_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_samples_name    ON samples(name);
CREATE INDEX IF NOT EXISTS idx_samples_order   ON samples(order_no);
CREATE INDEX IF NOT EXISTS idx_samples_deleted ON samples(is_deleted);

-- 샘플 상세 테이블
CREATE TABLE IF NOT EXISTS sample_details (
    id           BIGSERIAL PRIMARY KEY,
    sample_id    BIGINT       NOT NULL,
    detail_value VARCHAR(100) NULL,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_id   BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id   BIGINT       NULL,
    updated_at   TIMESTAMP    NULL
);

CREATE INDEX IF NOT EXISTS idx_sample_details_sample_id   ON sample_details(sample_id);
CREATE INDEX IF NOT EXISTS idx_sample_details_detail_value ON sample_details(detail_value);
CREATE INDEX IF NOT EXISTS idx_sample_details_deleted     ON sample_details(is_deleted);

-- 분석 job 테이블 (기존 테이블 컬럼 누락분 패치)
ALTER TABLE IF EXISTS analysis_jobs ADD COLUMN IF NOT EXISTS lease_type VARCHAR(100) NULL;

-- 분석 job 테이블
CREATE TABLE IF NOT EXISTS analysis_jobs (
    id           BIGSERIAL PRIMARY KEY,
    job_id       VARCHAR(255) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    file_size    BIGINT       NOT NULL,
    status       VARCHAR(50)  NOT NULL,
    step         VARCHAR(100) NULL,
    result       TEXT         NULL,
    description  TEXT         NULL,
    safety_level VARCHAR(50)  NULL,
    address      VARCHAR(500) NULL,
    lease_type   VARCHAR(100) NULL,
    user_id      BIGINT       NULL,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_id   BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id   BIGINT       NULL,
    updated_at   TIMESTAMP    NULL,

    CONSTRAINT uk_analysis_jobs_job_id UNIQUE (job_id)
);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status  ON analysis_jobs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_step    ON analysis_jobs(step);

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    id               BIGSERIAL PRIMARY KEY,
    kakao_id         BIGINT        NOT NULL,
    nickname         VARCHAR(100)  NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_id       BIGINT        NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id       BIGINT        NULL,
    updated_at       TIMESTAMP     NULL,

    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
);

CREATE INDEX IF NOT EXISTS idx_users_kakao_id ON users(kakao_id);
CREATE INDEX IF NOT EXISTS idx_users_deleted  ON users(is_deleted);

-- 사용자 디바이스 테이블 (FCM 토큰 관리)
CREATE TABLE IF NOT EXISTS user_devices (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    fcm_token   VARCHAR(512)  NOT NULL,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_id  BIGINT        NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id  BIGINT        NULL,
    updated_at  TIMESTAMP     NULL,

    CONSTRAINT uk_user_devices_fcm_token UNIQUE (fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_user_id ON user_devices(user_id);
