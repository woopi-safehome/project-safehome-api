-- 참조 구현(_sample) 제거에 딸린 일회성 정리.
--
-- 초기화 SQL 에서 테이블 정의를 지워도 이미 떠 있는 DB 의 테이블은 남는다.
-- 자동 DDL 도 마이그레이션 도구도 없으므로 이 파일은 자동으로 실행되지 않는다.
-- 사람이 한 번 판단해서 돌린다.
--
--   docker exec -i <postgres 컨테이너> psql -U <사용자> -d <DB> < drop-sample-tables.sql
--
-- 돌리기 전에 확인할 것: 이 두 테이블을 읽는 코드가 저장소에 남아 있지 않아야 한다.
--   git grep -i "samples\|sample_details" -- '*.kt'

DROP TABLE IF EXISTS sample_details;
DROP TABLE IF EXISTS samples;
