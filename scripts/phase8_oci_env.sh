#!/usr/bin/env bash

# Phase 8 OCI environment hydration.
# Intended to be sourced:
#   source scripts/phase8_oci_env.sh

OCI_PROFILE="${OCI_PROFILE:-phase8-dev}"
OCI_REGION="${OCI_REGION:-ap-tokyo-1}"
OCI_INSTANCE_NAME="${OCI_INSTANCE_NAME:-instance-20260728-0108}"

TENANCY_ID="$(
  awk -F= -v profile="$OCI_PROFILE" '
    $0 == "[" profile "]" { in_profile=1; next }
    /^\[/ { in_profile=0 }
    in_profile && /^tenancy=/ { print $2; exit }
  ' "$HOME/.oci/config"
)"

if [ -z "$TENANCY_ID" ]; then
  echo "ERROR: TENANCY_ID could not be resolved"
  return 1 2>/dev/null || exit 1
fi

echo "Resolving OCI instance..."

INSTANCE_ID="$(
  oci compute instance list \
    --profile "$OCI_PROFILE" \
    --auth security_token \
    --compartment-id "$TENANCY_ID" \
    --region "$OCI_REGION" \
    --display-name "$OCI_INSTANCE_NAME" \
    --query 'data[0].id' \
    --raw-output
)" || {
  echo "ERROR: OCI instance lookup failed"
  return 1 2>/dev/null || exit 1
}

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "null" ]; then
  echo "ERROR: INSTANCE_ID could not be resolved"
  return 1 2>/dev/null || exit 1
fi

echo "Resolving primary VNIC..."

VNIC_JSON="$(
  oci compute instance list-vnics \
    --profile "$OCI_PROFILE" \
    --auth security_token \
    --instance-id "$INSTANCE_ID" \
    --output json
)" || {
  echo "ERROR: VNIC lookup failed"
  return 1 2>/dev/null || exit 1
}

VNIC_ID="$(
  printf '%s' "$VNIC_JSON" |
  python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
print(d[0]["id"] if d else "")
'
)"

PUBLIC_IP="$(
  printf '%s' "$VNIC_JSON" |
  python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
print(d[0].get("public-ip") or "" if d else "")
'
)"

export OCI_PROFILE OCI_REGION OCI_INSTANCE_NAME
export TENANCY_ID INSTANCE_ID VNIC_ID PUBLIC_IP
export TF_VAR_tenancy_ocid="$TENANCY_ID"

echo
echo "===== PHASE 8 OCI ENV ====="
printf 'TENANCY_ID=READY length=%s\n' "${#TENANCY_ID}"
printf 'INSTANCE_ID=READY length=%s\n' "${#INSTANCE_ID}"
printf 'VNIC_ID=READY length=%s\n' "${#VNIC_ID}"
printf 'PUBLIC_IP=%s\n' "$PUBLIC_IP"
