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

-- 분석 job 테이블
CREATE TABLE IF NOT EXISTS analysis_jobs (
    id          BIGSERIAL PRIMARY KEY,
    job_id      VARCHAR(255) NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_size   BIGINT       NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    step        VARCHAR(100) NULL,
    result      TEXT         NULL,
    description TEXT         NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_id  BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id  BIGINT       NULL,
    updated_at  TIMESTAMP    NULL,

    CONSTRAINT uk_analysis_jobs_job_id UNIQUE (job_id)
);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status  ON analysis_jobs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_step    ON analysis_jobs(step);
