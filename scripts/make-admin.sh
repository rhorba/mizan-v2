#!/usr/bin/env bash
# Grants the realm role "admin" to an existing, signed-up user and requires TOTP set-up at their
# next login (docs/security §3: MFA on for the admin account). Keycloak's default browser flow then
# asks for the code on every login once TOTP is configured.
#   scripts/make-admin.sh someone@example.com
# Runs kcadm.sh inside the local Compose Keycloak, with the bootstrap admin from .env.
set -euo pipefail

email="${1:?usage: scripts/make-admin.sh <email>}"
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "missing .env: cp .env.example .env" >&2; exit 2; }
set -a; source .env; set +a

# Git Bash on Windows would rewrite /opt/... into a Windows path.
export MSYS_NO_PATHCONV=1

kcadm() { docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }

kcadm config credentials --server http://localhost:8080 --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

user_id=$(kcadm get users -r mizan -q "email=$email" -q exact=true --fields id --format csv --noquotes)
if [[ -z "$user_id" ]]; then
  echo "no user with e-mail $email in realm mizan (sign up first)" >&2
  exit 1
fi

kcadm add-roles -r mizan --uid "$user_id" --rolename admin
kcadm update "users/$user_id" -r mizan -s 'requiredActions=["CONFIGURE_TOTP"]'
echo "admin role granted to $email; TOTP set-up required at next login"
