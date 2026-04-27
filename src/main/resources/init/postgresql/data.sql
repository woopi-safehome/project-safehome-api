-- 샘플 마스터 데이터
INSERT INTO samples (name, code, description, order_no, is_deleted, created_id, created_at, updated_id, updated_at)
VALUES
    ('샘플 A', 'SMP-A', '샘플 A 설명입니다.', 1, FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP),
    ('샘플 B', 'SMP-B', '샘플 B 설명입니다.', 2, FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP),
    ('샘플 C', 'SMP-C', '샘플 C 설명입니다.', 3, FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 샘플 상세 데이터 (A)
INSERT INTO sample_details (sample_id, detail_value, is_deleted, created_id, created_at, updated_id, updated_at)
VALUES
    (1, '샘플 A 상세값 1', FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP),
    (1, '샘플 A 상세값 2', FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 샘플 상세 데이터 (B)
INSERT INTO sample_details (sample_id, detail_value, is_deleted, created_id, created_at, updated_id, updated_at)
VALUES
    (2, '샘플 B 상세값 1', FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP),
    (2, '샘플 B 상세값 2', FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 샘플 상세 데이터 (C)
INSERT INTO sample_details (sample_id, detail_value, is_deleted, created_id, created_at, updated_id, updated_at)
VALUES
    (3, '샘플 C 상세값 1', FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP),
    (3, '샘플 C 상세값 2', FALSE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
