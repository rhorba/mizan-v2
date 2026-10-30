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

# Object Storage wants a Content-Length, so the encrypted dump goes to a file first.
pg_dump --format=custom --no-owner | age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$file"
curl --fail --silent --show-error --retry 3 --upload-file "$file" "${BACKUP_PAR_URL%/}/$name"
echo "backup: uploaded $name ($(wc -c < "$file") bytes)"
rm -f "$file"
