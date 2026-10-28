output "public_ip" {
  description = "Reserved address of the node (host: <ip>.sslip.io until a domain exists)"
  value       = oci_core_public_ip.node.ip_address
}

output "ssh" {
  value = "ssh ubuntu@${oci_core_public_ip.node.ip_address}"
}

output "availability_domain" {
  value = local.availability_domain
}

output "backup_bucket" {
  value = oci_objectstorage_bucket.backups.name
}
