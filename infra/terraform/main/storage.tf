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

# Story 7.3: "7 kept". One dump a night, so objects older than 7 days are deleted.
resource "oci_objectstorage_object_lifecycle_policy" "backups" {
  namespace = data.oci_objectstorage_namespace.this.namespace
  bucket    = oci_objectstorage_bucket.backups.name

  rules {
    name        = "keep-7-days"
    action      = "DELETE"
    is_enabled  = true
    time_amount = 7
    time_unit   = "DAYS"
  }

  depends_on = [oci_identity_policy.objectstorage_lifecycle]
}

# Lifecycle rules run as the Object Storage service, which needs this grant.
resource "oci_identity_policy" "objectstorage_lifecycle" {
  compartment_id = var.tenancy_ocid
  name           = "mizan-objectstorage-lifecycle"
  description    = "Object Storage deletes expired backups (Story 7.3)"
  statements = [
    "Allow service objectstorage-${var.region} to manage object-family in ${var.compartment_ocid == var.tenancy_ocid ? "tenancy" : "compartment id ${var.compartment_ocid}"}",
  ]
}

# The cluster's only credential for the bucket: it can add objects, never read, list or delete
# them, so a leaked URL exposes no backup. Renew before it expires (risks.md R-BACKUP-PAR).
resource "oci_objectstorage_preauthrequest" "backup_upload" {
  namespace    = data.oci_objectstorage_namespace.this.namespace
  bucket       = oci_objectstorage_bucket.backups.name
  name         = "mizan-backup-upload"
  access_type  = "AnyObjectWrite"
  time_expires = var.backup_upload_expires
}
