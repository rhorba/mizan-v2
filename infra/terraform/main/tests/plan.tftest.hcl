# Story 7.1 without an account or a cent: the plan says what the security list, the node and the
# buckets will be. terraform init -backend=false && terraform test

mock_provider "oci" {
  mock_data "oci_identity_availability_domains" {
    defaults = {
      availability_domains = [{ name = "AD-1" }, { name = "AD-2" }]
    }
  }
  mock_data "oci_core_images" {
    defaults = {
      images = [{ id = "ocid1.image.oc1..ubuntu2404" }]
    }
  }
  mock_data "oci_core_vnic_attachments" {
    defaults = {
      vnic_attachments = [{ vnic_id = "ocid1.vnic.oc1..node" }]
    }
  }
  mock_data "oci_core_private_ips" {
    defaults = {
      private_ips = [{ id = "ocid1.privateip.oc1..node" }]
    }
  }
  mock_data "oci_objectstorage_namespace" {
    defaults = {
      namespace = "testnamespace"
    }
  }
}

variables {
  tenancy_ocid     = "ocid1.tenancy.oc1..test"
  user_ocid        = "ocid1.user.oc1..test"
  fingerprint      = "00:00"
  private_key_path = "/dev/null"
  region           = "eu-marseille-1"
  compartment_ocid = "ocid1.tenancy.oc1..test"
  admin_cidr       = "203.0.113.7/32"
  ssh_public_key   = "ssh-ed25519 AAAAtest owner@test"
}

run "firewall_opens_only_ssh_from_owner_and_web" {
  command = plan

  assert {
    condition = toset([
      for r in oci_core_security_list.node.ingress_security_rules : r.tcp_options[0].min if r.protocol == "6"
    ]) == toset([22, 80, 443])
    error_message = "TCP ingress must be exactly 22, 80 and 443 (6443 and everything else closed)."
  }

  assert {
    condition = alltrue([
      for r in oci_core_security_list.node.ingress_security_rules :
      r.source == "203.0.113.7/32" if r.protocol == "6" && r.tcp_options[0].min == 22
    ])
    error_message = "SSH must only be open to admin_cidr."
  }

  assert {
    condition     = length(oci_core_default_security_list.empty.ingress_security_rules) == 0
    error_message = "The VCN's default security list must stay empty (it allows SSH from anywhere)."
  }

  assert {
    # Ids are unknown at plan; network.tf passes only the mizan-node list.
    condition     = length(oci_core_subnet.public.security_list_ids) == 1
    error_message = "The subnet must only use the mizan-node security list."
  }
}

run "node_is_the_always_free_a1_allowance" {
  command = plan

  assert {
    condition     = oci_core_instance.node.shape == "VM.Standard.A1.Flex" && oci_core_instance.node.shape_config[0].ocpus == 4 && oci_core_instance.node.shape_config[0].memory_in_gbs == 24
    error_message = "One A1.Flex with 4 OCPU / 24 GB (the whole Always Free allowance)."
  }

  assert {
    condition     = oci_core_instance.node.source_details[0].boot_volume_size_in_gbs == "100"
    error_message = "100 GB boot volume (Always Free: 200 GB total)."
  }

  assert {
    condition     = oci_core_instance.node.create_vnic_details[0].assign_public_ip == "false"
    error_message = "No ephemeral public IP: the reserved one is attached instead."
  }

  assert {
    condition     = oci_core_instance.node.instance_options[0].are_legacy_imds_endpoints_disabled
    error_message = "Legacy IMDS (v1) must be off."
  }

  assert {
    condition     = oci_core_public_ip.node.lifetime == "RESERVED"
    error_message = "The node's address must be reserved (survives re-creation)."
  }

  assert {
    condition     = strcontains(base64decode(oci_core_instance.node.metadata["user_data"]), "INSTALL_K3S_VERSION='v1.37.1+k3s1'")
    error_message = "cloud-init must install the pinned k3s version."
  }
}

run "buckets_are_private" {
  command = plan

  assert {
    condition     = oci_objectstorage_bucket.backups.access_type == "NoPublicAccess"
    error_message = "Backups must never be public."
  }
}

run "next_availability_domain_wraps_around" {
  command = plan

  variables {
    availability_domain_index = 3
  }

  assert {
    condition     = oci_core_instance.node.availability_domain == "AD-2"
    error_message = "Index 3 over two ADs must pick AD-2."
  }
}

run "ssh_is_never_open_to_the_world" {
  command = plan

  variables {
    admin_cidr = "0.0.0.0/0"
  }

  expect_failures = [var.admin_cidr]
}

run "ssh_is_never_open_to_a_network" {
  command = plan

  variables {
    admin_cidr = "196.64.101.0/24"
  }

  expect_failures = [var.admin_cidr]
}
