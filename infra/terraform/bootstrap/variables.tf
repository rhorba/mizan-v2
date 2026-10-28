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
