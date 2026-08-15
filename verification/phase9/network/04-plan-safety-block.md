# Phase 9.1 Destructive Plan Block

An initial hardened-network plan attempted to replace the OCI development
instance because `TF_VAR_ssh_authorized_key` contained an abbreviated
placeholder rather than the exact existing public key.

Observed behavior:

- Terraform detected a change to `metadata.ssh_authorized_keys`.
- The provider classified the metadata change as requiring instance replacement.
- `lifecycle.prevent_destroy` blocked the destructive plan.
- No `terraform apply` was executed.
- No OCI infrastructure mutation occurred.
- The input was corrected from existing Terraform state before replanning.

This result demonstrates that the brownfield destruction safeguard remained
effective during Phase 9.1.
