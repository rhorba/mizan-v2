# infra/terraform — Oracle Cloud Always Free (Story 7.1)

One A1.Flex node (4 OCPU / 24 GB, Ubuntu 24.04, k3s) behind a security list that opens 22 to the
owner's IP only and 80/443 to everyone. Design and gotchas:
`skills/devops-devsecops/references/oci-terraform.md`.

| Stack | State | Creates |
|---|---|---|
| `bootstrap/` | local (once) | private, versioned `mizan-tfstate` bucket |
| `main/` | `oci` backend in that bucket | VCN, subnet, gateway, routes, security lists, node, reserved IP, `mizan-backups` bucket |

## Inputs (git-ignored, never committed)

- `~/.oci/oci_api_key.pem` — API signing key; its public half is added in the console
  (Profile → Tokens and keys → API keys).
- `*/terraform.tfvars` — copy `main/terraform.tfvars.example` (bootstrap takes the first six values).
- `main/backend.hcl` — copy `main/backend.hcl.example`.
- `admin_cidr` — your current public IP `/32`; home IPs change, re-check before every apply.

## Run (pinned Terraform image; no local install needed)

```bash
TF=hashicorp/terraform:1.16.5@sha256:c7926feace05d0f7e73542842bf3945924e955a1f782cf000ccbb8d18fa42d77
tf() { docker run --rm -it -v "$PWD:/work" -v "$HOME/.oci:/root/.oci:ro" -w /work "$TF" "$@"; }

cd bootstrap && tf init && tf plan -out plan && tf apply plan            # once
cd ../main  && tf init -backend-config=backend.hcl && tf plan -out plan  # review, then:
tf apply plan
```

Offline checks (also in CI): `tf fmt -check -recursive`, `tf init -backend=false && tf validate`,
`tf test` in `main/` (mocked provider, no account needed).

A1 "Out of host capacity": eu-marseille-1 has a single availability domain — retry later. In a
multi-AD region set `availability_domain_index` to try the next one.
