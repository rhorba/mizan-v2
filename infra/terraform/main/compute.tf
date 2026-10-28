# Story 7.1 / SDR-3: the whole Always Free A1 allowance as one k3s node.

data "oci_identity_availability_domains" "all" {
  compartment_id = var.tenancy_ocid
}

data "oci_core_images" "ubuntu" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

locals {
  ads                 = data.oci_identity_availability_domains.all.availability_domains
  availability_domain = local.ads[var.availability_domain_index % length(local.ads)].name
}

resource "oci_core_instance" "node" {
  compartment_id      = var.compartment_ocid
  availability_domain = local.availability_domain
  display_name        = "mizan-node"
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = 4
    memory_in_gbs = 24
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = 100
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = false # the reserved IP below survives re-creation
    hostname_label   = "node"
  }

  instance_options {
    are_legacy_imds_endpoints_disabled = true
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml", {
      k3s_version = var.k3s_version
    }))
  }

  lifecycle {
    # A newer Ubuntu image or a cloud-init edit must never replace the running node.
    ignore_changes = [source_details[0].source_id, metadata["user_data"]]
  }
}

data "oci_core_vnic_attachments" "node" {
  compartment_id = var.compartment_ocid
  instance_id    = oci_core_instance.node.id
}

data "oci_core_private_ips" "node" {
  vnic_id = data.oci_core_vnic_attachments.node.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "node" {
  compartment_id = var.compartment_ocid
  display_name   = "mizan-node"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.node.private_ips[0].id
}
