terraform {
  required_version = ">= 1.12" # native "oci" backend
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "9.9.0"
    }
  }
  # Partial config: values in backend.hcl (git-ignored), see backend.hcl.example.
  backend "oci" {}
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}
