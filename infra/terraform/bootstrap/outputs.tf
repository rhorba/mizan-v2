output "namespace" {
  description = "Object Storage namespace, for ../main/backend.hcl"
  value       = data.oci_objectstorage_namespace.this.namespace
}

output "state_bucket" {
  value = oci_objectstorage_bucket.tfstate.name
}
