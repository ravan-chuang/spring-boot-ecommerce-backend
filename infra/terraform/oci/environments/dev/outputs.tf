output "vcn" {
  value = [
    for vcn in data.oci_core_vcns.springboot.virtual_networks : {
      name = vcn.display_name
      cidr = vcn.cidr_block
    }
  ]
}

output "subnet" {
  value = [
    for subnet in data.oci_core_subnets.public.subnets : {
      name = subnet.display_name
      cidr = subnet.cidr_block
    }
  ]
}

output "instance" {
  value = [
    for instance in data.oci_core_instances.springboot.instances : {
      name  = instance.display_name
      shape = instance.shape
      state = instance.state
    }
  ]
}
