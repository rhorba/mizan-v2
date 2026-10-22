#!/bin/sh
# Runs once, on the first start of an empty data volume (docs/database-mizan-v2.md §1):
# database "mizan" with schema "mizan" owned by mizan_app and schema "keycloak" owned by keycloak.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v mizan_pw="$MIZAN_DB_PASSWORD" -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE mizan_app LOGIN PASSWORD :'mizan_pw';
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_pw';

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE mizan FROM PUBLIC;
GRANT CONNECT ON DATABASE mizan TO mizan_app, keycloak;

CREATE SCHEMA mizan AUTHORIZATION mizan_app;
CREATE SCHEMA keycloak AUTHORIZATION keycloak;

ALTER ROLE mizan_app SET search_path = mizan;
ALTER ROLE keycloak SET search_path = keycloak;
SQL
