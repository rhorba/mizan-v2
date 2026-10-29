#!/usr/bin/env bash
# Story 7.2: creates or updates the preprod Kubernetes Secrets from the local .env, over SSH.
# Values travel on stdin (never in a command line or in Git) and are never printed.
#
# Preprod passwords use PREPROD_* keys so the weak local compose values are never reused. Empty
# ones are generated once and written back to .env, so re-runs keep them: PostgreSQL sets role
# passwords only when its volume is first initialised.
#
#   deploy/secrets.sh              (PREPROD_SSH=ubuntu@<ip> to target another node)
# After changing a value, restart the pods that read it (kubectl rollout restart).
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=.env
SSH_TARGET=${PREPROD_SSH:-ubuntu@144.24.200.83}

val() { grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true; }

need() {
  if [ -z "$(val "$1")" ]; then
    echo "secrets.sh: $1 is empty in $ENV_FILE" >&2
    exit 1
  fi
}

# Generated once, then kept in .env.
generated() {
  local key=$1
  if [ -z "$(val "$key")" ]; then
    sed -i "/^$key=/d" "$ENV_FILE"
    echo "$key=$(openssl rand -hex 24)" >> "$ENV_FILE"
    echo "secrets.sh: generated $key (saved in $ENV_FILE)" >&2
  fi
}

# shellcheck disable=SC2029 # expanded locally on purpose: arguments are fixed names
kubectl_remote() { ssh "$SSH_TARGET" sudo k3s kubectl "$@"; }

# secret <namespace> <name>: env-file lines on stdin.
# shellcheck disable=SC2029 # expanded locally on purpose: arguments are fixed names
secret() {
  ssh "$SSH_TARGET" "sudo k3s kubectl create namespace $1 --dry-run=client -o yaml | sudo k3s kubectl apply -f - >/dev/null \
    && sudo k3s kubectl -n $1 create secret generic $2 --from-env-file=/dev/stdin --dry-run=client -o yaml \
    | sudo k3s kubectl apply -f -"
}

for key in ANTHROPIC_API_KEY SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASSWORD SMTP_FROM; do need "$key"; done
for key in PREPROD_POSTGRES_PASSWORD PREPROD_MIZAN_DB_PASSWORD PREPROD_KEYCLOAK_DB_PASSWORD \
  PREPROD_KEYCLOAK_ADMIN_PASSWORD GRAFANA_ADMIN_PASSWORD; do
  generated "$key"
done
alert_email=$(val ALERT_EMAIL)
alert_email=${alert_email:-$(val SMTP_USER)}

secret platform postgres <<EOF
POSTGRES_PASSWORD=$(val PREPROD_POSTGRES_PASSWORD)
MIZAN_DB_PASSWORD=$(val PREPROD_MIZAN_DB_PASSWORD)
KEYCLOAK_DB_PASSWORD=$(val PREPROD_KEYCLOAK_DB_PASSWORD)
EOF

secret platform keycloak <<EOF
KC_DB_PASSWORD=$(val PREPROD_KEYCLOAK_DB_PASSWORD)
KC_BOOTSTRAP_ADMIN_USERNAME=admin
KC_BOOTSTRAP_ADMIN_PASSWORD=$(val PREPROD_KEYCLOAK_ADMIN_PASSWORD)
SMTP_HOST=$(val SMTP_HOST)
SMTP_PORT=$(val SMTP_PORT)
SMTP_USER=$(val SMTP_USER)
SMTP_PASSWORD=$(val SMTP_PASSWORD)
SMTP_FROM=$(val SMTP_FROM)
EOF

secret mizan mizan-api <<EOF
SPRING_DATASOURCE_PASSWORD=$(val PREPROD_MIZAN_DB_PASSWORD)
EOF

secret mizan mizan-analyzer <<EOF
ANTHROPIC_API_KEY=$(val ANTHROPIC_API_KEY)
EOF

secret observability grafana <<EOF
GF_SECURITY_ADMIN_PASSWORD=$(val GRAFANA_ADMIN_PASSWORD)
GF_SMTP_HOST=$(val SMTP_HOST):$(val SMTP_PORT)
GF_SMTP_USER=$(val SMTP_USER)
GF_SMTP_PASSWORD=$(val SMTP_PASSWORD)
GF_SMTP_FROM_ADDRESS=$(val SMTP_FROM)
ALERT_EMAIL=$alert_email
EOF

for namespace in platform mizan observability; do
  kubectl_remote -n "$namespace" get secrets --field-selector type=Opaque -o name
done
