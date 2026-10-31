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

1. The five GHCR packages are public (GitHub → profile → Packages → each → visibility).
2. `.env` has `LETSENCRYPT_EMAIL`, `SMTP_*`, `ANTHROPIC_API_KEY`, `BACKUP_*` (see `.env.example`).
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

## Backups (Story 7.3)

Every night at 02:30 UTC the `platform/backup` CronJob dumps the `mizan` database (schemas
`mizan` and `keycloak`), encrypts it to the age public key `BACKUP_AGE_RECIPIENT` and uploads it
through an upload-only URL to the `mizan-backups` bucket; objects older than 7 days are deleted.
Only the private key `~/.mizan/backup-age.key` (copy in the owner's Google Drive) can read a dump.

```bash
# Run one now
ssh ubuntu@144.24.200.83 sudo k3s kubectl -n platform create job --from=cronjob/backup backup-manual
# List and download (OCI CLI, owner's API key; read access is never given to the cluster)
oci os object list -bn mizan-backups --query 'data[].{name:name,size:size}' --output table
oci os object get -bn mizan-backups --name mizan-<timestamp>.dump.age --file mizan.dump.age
```

Restore test (throwaway database on the owner's machine; the backup image has age and pg_restore):

```bash
docker network create restore
docker run -d --name restore-pg --network restore -e POSTGRES_PASSWORD=pw \
  postgres:18.6-alpine
docker run --rm -i --network restore -e PGPASSWORD=pw -v ~/.mizan:/k:ro \
  --entrypoint sh ghcr.io/rhorba/mizan-backup:<sha> -c '
    age -d -i /k/backup-age.key > /tmp/mizan.dump &&
    createdb -h restore-pg -U postgres mizan &&
    pg_restore -h restore-pg -U postgres -d mizan --no-owner /tmp/mizan.dump &&
    psql -h restore-pg -U postgres -d mizan -c "select name from keycloak.realm"       -c "select count(*) from mizan.contracts"' < mizan.dump.age
docker rm -f restore-pg && docker network rm restore
```

In Git Bash, `export MSYS_NO_PATHCONV=1` first. If Docker Desktop's proxy answers `denied` for
ghcr.io although the image is public, build it instead:
`docker build -t local/mizan-backup -f docker/backup.Dockerfile docker`.

Verified 2026-10-31 with `mizan-20261031T111237Z.dump.age`: 107 tables restored, row counts
identical to live table by table, realms `master` and `mizan` present. The business tables were
empty on preprod that day, so repeat this once real contracts exist.

Restore into preprod (replaces current data; not rehearsed yet). Argo CD self-heal would scale the writers straight
back up, so pause auto-sync for the duration:

```bash
K="ssh ubuntu@144.24.200.83 sudo k3s kubectl"
$K -n argocd patch application mizan-preprod --type merge -p '{"spec":{"syncPolicy":{"automated":null}}}'
$K -n mizan scale deploy/mizan-api deploy/mizan-analyzer --replicas=0
$K -n platform scale deploy/keycloak --replicas=0
# decrypt locally as above, then stream the dump into the server:
$K -n platform exec -i postgres-0 -- pg_restore -U postgres -d mizan --clean --if-exists --no-owner < mizan.dump
# re-enable auto-sync (as in deploy/argocd/application.yaml); it scales everything back up
$K apply -f - < deploy/argocd/application.yaml
```
