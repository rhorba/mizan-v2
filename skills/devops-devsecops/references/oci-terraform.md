# OCI + Terraform Reference — Always Free single node

Load before touching `infra/terraform`. Decisions: docs/devops §3, SDR-3 (system design).

## 1. Always Free limits that shape the code

| Resource | Free allowance | We use |
|---|---|---|
| Ampere A1 (VM.Standard.A1.Flex) | 4 OCPU + 24 GB total, **home region only** | 1 instance, all of it |
| Block/boot volume | 200 GB total | 100 GB boot |
| Object Storage | 20 GB | tfstate + backups bucket |
| Reserved public IP | 1 free | 1 |

Anything beyond these bills (or fails): never add a second A1, a load balancer or block volumes
without checking. The home region is fixed at sign-up.

## 2. Gotchas

- **"Out of host capacity"** on A1 is common. Terraform cannot retry itself: pick the AD by index
  (`availability_domain_index`). Our home region eu-marseille-1 has a **single AD**, so the only
  retry is "later" — no retry script (YAGNI).
- **Paths inside the Terraform container**: `~` is the container's home; pass the SSH public key
  as contents (`ssh_public_key`), mount `~/.oci` read-only for the API key.
- **Default security list** of a VCN allows SSH from 0.0.0.0/0. Attach only our own list to the
  subnet *and* manage `oci_core_default_security_list` with no rules, so nothing can fall back to it.
- **Ubuntu images on OCI ship host iptables** (`/etc/iptables/rules.v4`) that REJECT everything but
  22 on INPUT and FORWARD — that breaks 80/443 and k3s pod networking. The VCN security list is our
  firewall; cloud-init replaces the host rules with ACCEPT and k3s manages its own chains.
- **Ephemeral vs reserved IP**: create the VNIC with `assign_public_ip = false` and attach a
  `RESERVED` `oci_core_public_ip` to the primary private IP, so the address survives re-creation
  (DNS / sslip.io host stays valid).
- **Image drift**: the latest Ubuntu image OCID changes; `ignore_changes` on the image so a new
  image never replaces the running node.
- **IMDS v1** off: `instance_options.are_legacy_imds_endpoints_disabled = true`.

## 3. State

- Native `oci` backend (Terraform ≥ 1.12): `bucket`, `namespace` required; API-key auth needs
  `tenancy_ocid`, `user_ocid`, `fingerprint`, `private_key_path`, `region`. Locking via a `.lock`
  object (If-None-Match). Backend blocks cannot use variables → partial config in `backend.hcl`
  (git-ignored; `backend.hcl.example` committed) and `terraform init -backend-config=backend.hcl`.
- Chicken and egg: `bootstrap/` (local state) creates the versioned, private state bucket once;
  `main/` stores its state there.

## 4. Secrets and inputs

- `*.tfvars`, `*.pem`, `backend.hcl` are git-ignored. Commit only `*.example` files.
- The API private key stays in `~/.oci/` on the owner's machine; Terraform reads it by path.
- `admin_cidr` is validated: a single host (/32), never 0.0.0.0/0. Home IPs change → update and
  re-apply (security list only, no instance change).

## 5. Verifying without an account

- `terraform fmt -check -recursive`, `terraform init -backend=false && terraform validate`.
- `terraform test` with `mock_provider "oci"` + `override_data` for ADs/images: asserts the plan
  (ports, CIDRs, shape, reserved IP, bucket privacy) with no credentials and no cost.
- Trivy `config` scan covers the IaC in CI (security job).
- Run Terraform from the pinned `hashicorp/terraform` image if the local binary is older.
