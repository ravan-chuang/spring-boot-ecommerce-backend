#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/deploy_image_digest.sh <image@sha256:digest> [namespace] [deployment] [container]

Example:
  ./scripts/deploy_image_digest.sh \
    ghcr.io/example/repository@sha256:abcd... \
    spring-boot-lab \
    spring-boot-lab \
    app
EOF
}

IMAGE_REF="${1:-}"
NAMESPACE="${2:-spring-boot-lab}"
DEPLOYMENT="${3:-spring-boot-lab}"
CONTAINER="${4:-app}"

if [[ -z "$IMAGE_REF" ]]; then
  usage
  exit 1
fi

if [[ ! "$IMAGE_REF" =~ @sha256:[a-fA-F0-9]{64}$ ]]; then
  echo "ERROR: image must be pinned to a sha256 digest."
  echo "Received: $IMAGE_REF"
  exit 1
fi

echo "Deploying immutable image:"
echo "  image      = $IMAGE_REF"
echo "  namespace  = $NAMESPACE"
echo "  deployment = $DEPLOYMENT"
echo "  container  = $CONTAINER"

kubectl set image \
  "deployment/${DEPLOYMENT}" \
  "${CONTAINER}=${IMAGE_REF}" \
  -n "$NAMESPACE"

kubectl rollout status \
  "deployment/${DEPLOYMENT}" \
  -n "$NAMESPACE" \
  --timeout=300s

ACTUAL_IMAGE="$(
  kubectl get deployment "$DEPLOYMENT" \
    -n "$NAMESPACE" \
    -o jsonpath="{.spec.template.spec.containers[?(@.name=='${CONTAINER}')].image}"
)"

echo
echo "Deployment image:"
echo "$ACTUAL_IMAGE"

if [[ "$ACTUAL_IMAGE" != "$IMAGE_REF" ]]; then
  echo "ERROR: deployment image does not match requested digest."
  exit 1
fi

echo
echo "Digest deployment verification: PASS"
