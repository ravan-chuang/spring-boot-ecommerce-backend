data "oci_core_vcns" "springboot" {
  compartment_id = var.tenancy_ocid
  display_name   = "springboot-vcn"
}

data "oci_core_subnets" "public" {
  compartment_id = var.tenancy_ocid
  display_name   = "public-subnet"
}

data "oci_core_instances" "springboot" {
  compartment_id = var.tenancy_ocid
  display_name   = var.instance_name
}

resource "oci_core_vcn" "springboot" {
  compartment_id = var.tenancy_ocid
  cidr_block     = "10.0.0.0/16"
  display_name   = "springboot-vcn"

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_internet_gateway" "springboot" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.springboot.id
  display_name   = "ig-quick-action-IGW"
  enabled        = true

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_route_table" "springboot_default" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.springboot.id
  display_name   = "Default Route Table for springboot-vcn"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.springboot.id
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_security_list" "springboot_default" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.springboot.id
  display_name   = "Default Security List for springboot-vcn"

  egress_security_rules {
    protocol         = "all"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    stateless        = false
  }

  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false

    tcp_options {
      min = 22
      max = 22
    }
  }

  ingress_security_rules {
    protocol    = "1"
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false

    icmp_options {
      type = 3
      code = 4
    }
  }

  ingress_security_rules {
    protocol    = "1"
    source      = "10.0.0.0/16"
    source_type = "CIDR_BLOCK"
    stateless   = false

    icmp_options {
      type = 3
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_subnet" "public" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.springboot.id

  cidr_block   = "10.0.0.0/24"
  display_name = "public-subnet"
  dns_label    = "publicsubnet"

  route_table_id    = oci_core_route_table.springboot_default.id
  security_list_ids = [oci_core_security_list.springboot_default.id]

  prohibit_public_ip_on_vnic = false
  prohibit_internet_ingress  = false

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_network_security_group" "springboot_ingress" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.springboot.id
  display_name   = "ig-quick-action-NSG"

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_network_security_group" "springboot_egress_legacy" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.springboot.id
  display_name   = "ig-quick-action-NSG"

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_network_security_group_security_rule" "ingress_egress_all" {
  network_security_group_id = oci_core_network_security_group.springboot_ingress.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
}

resource "oci_core_network_security_group_security_rule" "ssh" {
  network_security_group_id = oci_core_network_security_group.springboot_ingress.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "SSH"

  tcp_options {
    destination_port_range {
      min = 22
      max = 22
    }
  }
}

resource "oci_core_network_security_group_security_rule" "http" {
  network_security_group_id = oci_core_network_security_group.springboot_ingress.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "HTTP"

  tcp_options {
    destination_port_range {
      min = 80
      max = 80
    }
  }
}

resource "oci_core_network_security_group_security_rule" "https" {
  network_security_group_id = oci_core_network_security_group.springboot_ingress.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "HTTPS"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "spring_boot" {
  network_security_group_id = oci_core_network_security_group.springboot_ingress.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "Spring Boot"

  tcp_options {
    destination_port_range {
      min = 8080
      max = 8080
    }
  }
}

resource "oci_core_network_security_group_security_rule" "legacy_egress_all" {
  network_security_group_id = oci_core_network_security_group.springboot_egress_legacy.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
}

resource "oci_core_instance" "springboot" {
  compartment_id      = var.tenancy_ocid
  availability_domain = "tjZz:AP-TOKYO-1-AD-1"
  fault_domain        = "FAULT-DOMAIN-1"

  display_name = "instance-20260728-0108"
  shape        = "VM.Standard.E2.1.Micro"

  create_vnic_details {
    subnet_id              = oci_core_subnet.public.id
    hostname_label         = "instance-20260728-0108"
    assign_public_ip       = true
    skip_source_dest_check = false

    nsg_ids = [
      oci_core_network_security_group.springboot_ingress.id,
      oci_core_network_security_group.springboot_egress_legacy.id,
    ]
  }

  source_details {
    source_type = "image"
    source_id   = var.instance_image_ocid
  }

  metadata = {
    ssh_authorized_keys = var.ssh_authorized_key
  }

  instance_options {
    are_legacy_imds_endpoints_disabled = true
  }

  lifecycle {
    prevent_destroy = true
  }
}
