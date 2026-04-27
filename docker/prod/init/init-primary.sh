#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER $POSTGRES_REPLICATION_USER WITH REPLICATION ENCRYPTED PASSWORD '$POSTGRES_REPLICATION_PASSWORD';
EOSQL

cat >> /var/lib/postgresql/data/postgresql.conf <<EOF

# Replication
wal_level = replica
max_wal_senders = 3
wal_keep_size = 64
hot_standby = on
EOF

echo "host replication ${POSTGRES_REPLICATION_USER} all md5" >> /var/lib/postgresql/data/pg_hba.conf
