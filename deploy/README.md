# Preprod runbook (Story 7.2)

One k3s node on OCI (`infra/terraform`, 144.24.200.83). Argo CD in the cluster pulls; CI never
touches the cluster API (port 6443 is closed).

| URL | What |
|---|---|
| https://144.24.200.83.sslip.io | App (`/api`, `/otlp/v1/traces` on the same origin) |
| https://auth.144.24.200.83.sslip.io | Keycloak login (`/realms/mizan`, `/resources` only; master realm and admin via port-forward) |
| https://grafana.144.24.200.83.sslip.io | Grafana (user `admin`, `GRAFANA_ADMIN_PASSWORD` in `.env`) |

## How a change reaches preprod

merge to `main` → CI gates → `publish` (amd64 + arm64 images to `ghcr.io/rhorba/mizan-*:<sha>`,
Trivy) → `promote` (force-pushes `env/preprod` = `main@<sha>` + image tags) → Argo CD syncs
`deploy/overlays/preprod` from `env/preprod`. Roll back: revert on `main`.

## First time (once)

1. The four GHCR packages are public (GitHub → profile → Packages → each → visibility).
2. `.env` has `LETSENCRYPT_EMAIL`, `SMTP_*`, `ANTHROPIC_API_KEY` (see `.env.example`).
3. `deploy/bootstrap.sh` — cert-manager, Argo CD, ClusterIssuer, Secrets, Application.

## Day to day

```bash
ssh ubuntu@144.24.200.83 sudo k3s kubectl -n argocd get application mizan-preprod
ssh ubuntu@144.24.200.83 sudo k3s kubectl get pods -A
deploy/secrets.sh   # after changing .env; then: kubectl -n <ns> rollout restart deploy/<name>
```

Admin consoles stay private — tunnel and port-forward:

```bash
ssh -L 8443:localhost:8443 ubuntu@144.24.200.83 \
  sudo k3s kubectl -n argocd port-forward svc/argo-cd-argocd-server 8443:443         # Argo CD
ssh -L 8180:localhost:8180 ubuntu@144.24.200.83 \
  sudo k3s kubectl -n platform port-forward svc/keycloak 8180:8080          # Keycloak admin
```

SSH only works from `admin_cidr` (`infra/terraform`); a new home IP means updating it and
re-applying (R-ADMIN-IP).
