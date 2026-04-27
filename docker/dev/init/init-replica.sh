#!/bin/bash
set -e

echo "Primary 준비 대기 중..."
until pg_isready -h postgres-primary -p 5432 -U "$POSTGRES_USER"; do
  sleep 2
done

echo "pg_basebackup 시작..."
PGPASSWORD=$POSTGRES_REPLICATION_PASSWORD pg_basebackup \
  -h postgres-primary \
  -D /var/lib/postgresql/data \
  -U "$POSTGRES_REPLICATION_USER" \
  -Xs -R -P

echo "Replica 초기화 완료"
