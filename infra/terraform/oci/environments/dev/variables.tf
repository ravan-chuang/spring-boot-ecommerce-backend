variable "oci_profile" {
  description = "OCI CLI profile used for Security Token authentication."
  type        = string
  default     = "phase8-dev"
}

variable "region" {
  description = "OCI region."
  type        = string
  default     = "ap-tokyo-1"
}

variable "tenancy_ocid" {
  description = "OCI tenancy OCID."
  type        = string
}

variable "instance_name" {
  description = "Existing OCI compute instance display name."
  type        = string
  default     = "instance-20260728-0108"
}

variable "instance_image_ocid" {
  description = "Existing OCI image OCID used by the dev compute instance."
  type        = string
}

variable "ssh_authorized_key" {
  description = "SSH public key configured on the dev compute instance."
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "CIDR permitted to access SSH on the OCI development VM."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.ssh_allowed_cidr)) && var.ssh_allowed_cidr != "0.0.0.0/0"
    error_message = "ssh_allowed_cidr must be a valid restricted CIDR and must not be 0.0.0.0/0."
  }
}
