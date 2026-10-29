#!/usr/bin/env bash
# Story 7.2: one-time bootstrap of the preprod k3s node, over SSH (the cluster API stays closed).
#   1. cert-manager and Argo CD through k3s's Helm controller (deploy/argocd/*.yaml)
#   2. the Let's Encrypt ClusterIssuer, with LETSENCRYPT_EMAIL from .env (kept out of Git)
#   3. Secrets (deploy/secrets.sh)
#   4. the Argo CD Application; from then on every merge to main rolls out by itself
# Safe to re-run: every step is an apply.
set -euo pipefail

cd "$(dirname "$0")/.."
SSH_TARGET=${PREPROD_SSH:-ubuntu@144.24.200.83}

val() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }
# shellcheck disable=SC2029 # expanded locally on purpose: arguments are fixed names
kubectl_remote() { ssh "$SSH_TARGET" sudo k3s kubectl "$@"; }

# Waits until the Helm controller has created a deployment, then until it is rolled out.
rolled_out() {
  local namespace=$1 deployment=$2
  for _ in $(seq 1 60); do
    kubectl_remote -n "$namespace" get deployment "$deployment" >/dev/null 2>&1 && break
    sleep 5
  done
  kubectl_remote -n "$namespace" rollout status deployment "$deployment" --timeout=300s
}

email=$(val LETSENCRYPT_EMAIL)
if [ -z "$email" ]; then
  echo "bootstrap.sh: LETSENCRYPT_EMAIL is empty in .env" >&2
  exit 1
fi

echo "== 1/4 cert-manager and Argo CD"
kubectl_remote apply -f - < deploy/argocd/cert-manager.yaml
kubectl_remote apply -f - < deploy/argocd/argocd.yaml
for deployment in cert-manager cert-manager-webhook cert-manager-cainjector; do
  rolled_out cert-manager "$deployment"
done

echo "== 2/4 ClusterIssuer letsencrypt"
kubectl_remote apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: $email
    privateKeySecretRef:
      name: letsencrypt-account
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
EOF

echo "== 3/4 Secrets"
deploy/secrets.sh

echo "== 4/4 Argo CD Application"
for deployment in argocd-server argocd-repo-server; do
  rolled_out argocd "$deployment"
done
kubectl_remote apply -f - < deploy/argocd/application.yaml
echo "Done. Watch: ssh $SSH_TARGET sudo k3s kubectl -n argocd get application mizan-preprod -w"
