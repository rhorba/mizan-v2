# Story 7.1 / docs/devops §3: one public subnet; the security list is the firewall
# (22 from the owner only, 80/443 from anywhere, 6443 and everything else closed).

resource "oci_core_vcn" "mizan" {
  compartment_id = var.compartment_ocid
  display_name   = "mizan"
  cidr_blocks    = ["10.0.0.0/16"]
  dns_label      = "mizan"
}

# OCI's default list allows SSH from 0.0.0.0/0: keep it, but empty.
resource "oci_core_default_security_list" "empty" {
  manage_default_resource_id = oci_core_vcn.mizan.default_security_list_id
  display_name               = "mizan-default-empty"
}

resource "oci_core_internet_gateway" "mizan" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.mizan.id
  display_name   = "mizan"
  enabled        = true
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.mizan.id
  display_name   = "mizan-public"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.mizan.id
  }
}

resource "oci_core_security_list" "node" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.mizan.id
  display_name   = "mizan-node"

  egress_security_rules {
    description = "Updates, images, Let's Encrypt, Claude API, SMTP"
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  ingress_security_rules {
    description = "SSH from the owner only"
    source      = var.admin_cidr
    protocol    = "6"
    tcp_options {
      min = 22
      max = 22
    }
  }

  ingress_security_rules {
    description = "HTTP (ACME challenge, redirect to HTTPS)"
    source      = "0.0.0.0/0"
    protocol    = "6"
    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    description = "HTTPS"
    source      = "0.0.0.0/0"
    protocol    = "6"
    tcp_options {
      min = 443
      max = 443
    }
  }

  ingress_security_rules {
    description = "Path MTU discovery"
    source      = "0.0.0.0/0"
    protocol    = "1"
    icmp_options {
      type = 3
      code = 4
    }
  }
}

resource "oci_core_subnet" "public" {
  compartment_id    = var.compartment_ocid
  vcn_id            = oci_core_vcn.mizan.id
  display_name      = "mizan-public"
  cidr_block        = "10.0.1.0/24"
  dns_label         = "public"
  route_table_id    = oci_core_route_table.public.id
  security_list_ids = [oci_core_security_list.node.id]
}
