#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../.env"

BACKUP_DIR="/backups"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/safehome_$TIMESTAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

echo "[$TIMESTAMP] 백업 시작: $BACKUP_FILE"

PGPASSWORD=$POSTGRES_PASSWORD pg_dump \
  -h 127.0.0.1 \
  -p 5432 \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  | gzip > "$BACKUP_FILE"

# 보관 기간 초과 파일 삭제
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "[$TIMESTAMP] 백업 완료 (${RETENTION_DAYS}일 초과분 삭제)"
