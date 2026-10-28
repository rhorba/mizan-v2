variable "tenancy_ocid" {
  type = string
}

variable "user_ocid" {
  type = string
}

variable "fingerprint" {
  type = string
}

variable "private_key_path" {
  description = "API signing key on the owner's machine (never committed)"
  type        = string
}

variable "region" {
  description = "Home region (Always Free A1 runs only there)"
  type        = string
}

variable "compartment_ocid" {
  description = "Where everything lives; the tenancy OCID means the root compartment"
  type        = string
}

variable "admin_cidr" {
  description = "The only address allowed to SSH, e.g. \"203.0.113.7/32\""
  type        = string

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0)) && endswith(var.admin_cidr, "/32")
    error_message = "admin_cidr must be a single host (/32); SSH is never open to a network."
  }
}

variable "ssh_public_key" {
  description = "OpenSSH public key installed for the ubuntu user (contents, not a path)"
  type        = string
}

variable "availability_domain_index" {
  description = "Which AD to try (0-based); apply.sh walks them on A1 capacity errors"
  type        = number
  default     = 0
}

variable "k3s_version" {
  description = "Pinned k3s release installed by cloud-init"
  type        = string
  default     = "v1.37.1+k3s1"
}
