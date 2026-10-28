# docs/devops §3: nightly encrypted pg_dump lands here (CronJob arrives with Story 7.2).

data "oci_objectstorage_namespace" "this" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "backups" {
  compartment_id = var.compartment_ocid
  namespace      = data.oci_objectstorage_namespace.this.namespace
  name           = "mizan-backups"
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
}
