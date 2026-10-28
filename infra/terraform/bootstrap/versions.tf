terraform {
  required_version = ">= 1.12" # native "oci" backend used by ../main
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "9.9.0"
    }
  }
  # Local state on purpose: this stack creates the bucket the main stack keeps its state in.
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}
