# Story 7.1: the Object Storage bucket that holds the main stack's state. Run once.

data "oci_objectstorage_namespace" "this" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "tfstate" {
  compartment_id = var.compartment_ocid
  namespace      = data.oci_objectstorage_namespace.this.namespace
  name           = "mizan-tfstate"
  access_type    = "NoPublicAccess"
  versioning     = "Enabled" # a bad apply can be rolled back to the previous state
  storage_tier   = "Standard"
}
