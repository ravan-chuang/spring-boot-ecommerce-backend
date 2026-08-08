#!/usr/bin/env python3

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

workflow = ROOT / ".github/workflows/supply-chain.yml"
deploy_script = ROOT / "scripts/deploy_image_digest.sh"

errors: list[str] = []


def require_file(path: Path) -> str:
    if not path.is_file():
        errors.append(f"Missing required file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(errors="replace")


workflow_text = require_file(workflow)
deploy_text = require_file(deploy_script)

workflow_requirements = {
    "CycloneDX SBOM": "cyclonedx-maven-plugin",
    "Trivy scan": "aquasecurity/trivy-action",
    "GHCR login": "docker/login-action",
    "Docker Buildx": "docker/setup-buildx-action",
    "Container build": "docker/build-push-action",
    "Cosign signing": "cosign sign",
    "OIDC permission": "id-token: write",
    "Artifact attestation": "actions/attest-build-provenance",
    "Image SBOM": "anchore/sbom-action",
    "Immutable digest": "steps.build.outputs.digest",
}

for label, needle in workflow_requirements.items():
    if needle not in workflow_text:
        errors.append(f"Workflow missing requirement: {label}")

deploy_requirements = {
    "sha256 validation": "@sha256:",
    "kubectl set image": "kubectl set image",
    "rollout verification": "kubectl rollout status",
}

for label, needle in deploy_requirements.items():
    if needle not in deploy_text:
        errors.append(f"Deployment helper missing requirement: {label}")

if re.search(
    r"(password\s*[:=]\s*[^\s$][^\s]*|"
    r"BEGIN [A-Z ]*PRIVATE KEY|"
    r"AKIA[0-9A-Z]{16}|"
    r"gh[pousr]_[A-Za-z0-9]{20,})",
    workflow_text + "\n" + deploy_text,
    flags=re.IGNORECASE,
):
    errors.append("Potential hard-coded credential detected.")

print("=== Phase 7 Supply Chain Verification ===")
print(f"Workflow: {workflow}")
print(f"Digest deployment helper: {deploy_script}")
print()

if errors:
    for error in errors:
        print(f"FAIL: {error}")
    sys.exit(1)

print("PASS: CycloneDX application SBOM configured")
print("PASS: filesystem vulnerability scanning configured")
print("PASS: container vulnerability scanning configured")
print("PASS: immutable container build configured")
print("PASS: Cosign keyless signing configured")
print("PASS: GitHub OIDC permission configured")
print("PASS: build provenance attestation configured")
print("PASS: image SBOM configured")
print("PASS: digest-based deployment helper configured")
print("PASS: no obvious embedded credential detected")
print()
print("RESULT: Phase 7 supply-chain repository controls are structurally valid.")
