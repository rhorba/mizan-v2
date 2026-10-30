#!/bin/sh
# Story 7.3: nightly backup. pg_dump of the "mizan" database (schemas mizan and keycloak),
# encrypted to the owner's age public key, uploaded through a write-only pre-authenticated
# request on the backup bucket (it can add objects, never read or list them). The bucket's
# lifecycle rule deletes objects after 7 days.
#
# Env: PGHOST, PGUSER, PGPASSWORD, PGDATABASE, BACKUP_AGE_RECIPIENT, BACKUP_PAR_URL (ends in /o/).
# Restore: deploy/README.md "Backups".
set -eu
# shellcheck disable=SC3040 # Alpine ash supports pipefail
set -o pipefail

: "${BACKUP_AGE_RECIPIENT:?}" "${BACKUP_PAR_URL:?}"

name="mizan-$(date -u +%Y%m%dT%H%M%SZ).dump.age"
file="/tmp/$name"

# k3s applies a new pod's NetworkPolicy rules a moment after it starts: the first connections
# are refused. Wait for PostgreSQL (up to 60 s) and let curl retry refused connections.
tries=0
until pg_isready --quiet --timeout=2; do
  tries=$((tries + 1))
  if [ "$tries" -ge 30 ]; then
    echo "backup: PostgreSQL unreachable after 60 s" >&2
    exit 1
  fi
  sleep 2
done

# Object Storage wants a Content-Length, so the encrypted dump goes to a file first.
pg_dump --format=custom --no-owner | age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$file"
curl --fail --silent --show-error --retry 5 --retry-connrefused --retry-delay 2 \
  --upload-file "$file" "${BACKUP_PAR_URL%/}/$name"
echo "backup: uploaded $name ($(wc -c < "$file") bytes)"
rm -f "$file"
