-- 셈플 마스터 테이블
CREATE TABLE IF NOT EXISTS samples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    name VARCHAR(100) NOT NULL COMMENT '이름',
    code VARCHAR(100) NOT NULL COMMENT '코드 (유니크)',
    description TEXT NULL COMMENT '설명',
    order_no INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '삭제 여부',
    created_id BIGINT NOT NULL COMMENT '생성자 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_id BIGINT NULL COMMENT '수정자 ID',
    updated_at TIMESTAMP NULL DEFAULT NULL COMMENT '수정 시간',

    UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_samples_name ON samples(name);
CREATE INDEX IF NOT EXISTS idx_samples_order ON samples(order_no);
CREATE INDEX IF NOT EXISTS idx_samples_deleted ON samples(is_deleted);

-- 셈플 상세 테이블
CREATE TABLE IF NOT EXISTS sample_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    sample_id BIGINT NOT NULL COMMENT '샘플 ID',
    detail_value VARCHAR(100) NULL COMMENT '상세 값',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '삭제 여부',
    created_id BIGINT NOT NULL COMMENT '생성자 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_id BIGINT NULL COMMENT '수정자 ID',
    updated_at TIMESTAMP NULL DEFAULT NULL COMMENT '수정 시간'
);

CREATE INDEX IF NOT EXISTS idx_sample_details_sample_id ON sample_details(sample_id);
CREATE INDEX IF NOT EXISTS idx_sample_details_detail_value ON sample_details(detail_value);
CREATE INDEX IF NOT EXISTS idx_sample_details_deleted ON sample_details(is_deleted);

-- 분석 job 테이블 (기존 테이블 컬럼 누락분 패치)
ALTER TABLE IF EXISTS analysis_jobs ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '삭제 여부';
ALTER TABLE IF EXISTS analysis_jobs ADD COLUMN IF NOT EXISTS user_id BIGINT NULL COMMENT '사용자 ID';
ALTER TABLE IF EXISTS analysis_jobs ADD COLUMN IF NOT EXISTS safety_level VARCHAR(50) NULL COMMENT '안전 등급(SAFE, CAUTION, DANGER)';
ALTER TABLE IF EXISTS analysis_jobs ADD COLUMN IF NOT EXISTS address VARCHAR(500) NULL COMMENT '부동산 주소';
ALTER TABLE IF EXISTS analysis_jobs ADD COLUMN IF NOT EXISTS lease_type VARCHAR(100) NULL COMMENT '임대차 유형';

CREATE TABLE IF NOT EXISTS analysis_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    job_id VARCHAR(255) NOT NULL COMMENT '작업 ID(UUID)',
    file_name VARCHAR(255) NOT NULL COMMENT '파일명',
    file_size BIGINT NOT NULL COMMENT '파일 크기',
    status VARCHAR(50) NOT NULL COMMENT '상태(예: PENDING, IN_PROGRESS, COMPLETED, FAILED)',
    step VARCHAR(100) NULL COMMENT '현재 단계(예 : PDF_PARSING, LLM_ANALYSIS, POST_PROCESSING)',
    result TEXT NULL COMMENT '분석 결과 JSON',
    description TEXT NULL COMMENT '설명 (에러메시지 등)',
    safety_level VARCHAR(50) NULL COMMENT '안전 등급(SAFE, CAUTION, DANGER)',
    address VARCHAR(500) NULL COMMENT '부동산 주소',
    lease_type VARCHAR(100) NULL COMMENT '임대차 유형',
    user_id BIGINT NULL COMMENT '사용자 ID',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '삭제 여부',
    created_id BIGINT NOT NULL COMMENT '생성자 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_id BIGINT NULL COMMENT '수정자 ID',
    updated_at TIMESTAMP NULL DEFAULT NULL COMMENT '수정 시간',

    UNIQUE KEY uk_analysis_jobs_job_id (job_id)
);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status ON analysis_jobs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_step ON analysis_jobs(step);

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    kakao_id BIGINT NOT NULL COMMENT '카카오 사용자 ID',
    nickname VARCHAR(100) NOT NULL COMMENT '닉네임',
    profile_image_url VARCHAR(500) NULL COMMENT '프로필 이미지 URL',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '삭제 여부',
    created_id BIGINT NOT NULL COMMENT '생성자 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_id BIGINT NULL COMMENT '수정자 ID',
    updated_at TIMESTAMP NULL DEFAULT NULL COMMENT '수정 시간',

    UNIQUE KEY uk_users_kakao_id (kakao_id)
);

CREATE INDEX IF NOT EXISTS idx_users_kakao_id ON users(kakao_id);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users(is_deleted);

-- 사용자 디바이스 테이블 (FCM 토큰 관리)
CREATE TABLE IF NOT EXISTS user_devices (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    fcm_token   VARCHAR(512)  NOT NULL,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_id  BIGINT        NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_id  BIGINT        NULL,
    updated_at  TIMESTAMP     NULL DEFAULT NULL,

    UNIQUE KEY uk_user_devices_fcm_token (fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_user_id ON user_devices(user_id);
