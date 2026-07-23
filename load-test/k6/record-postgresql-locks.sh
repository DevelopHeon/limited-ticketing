#!/usr/bin/env bash
set -eu

: "${DATABASE_URL:?DATABASE_URL is required}"

output_file="${1:-load-test/k6/results/postgresql-locks.csv}"
interval_seconds="${INTERVAL_SECONDS:-1}"

mkdir -p "$(dirname "$output_file")"
printf 'timestamp_utc,waiting_lock_count\n' > "$output_file"

while true; do
  waiting_lock_count="$(psql "$DATABASE_URL" -Atqc "
    select count(*)
    from pg_stat_activity
    where datname = current_database()
      and wait_event_type = 'Lock';
  ")"
  printf '%s,%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$waiting_lock_count" >> "$output_file"
  sleep "$interval_seconds"
done
