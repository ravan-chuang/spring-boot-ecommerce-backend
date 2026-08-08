# Phase 7 — Software Supply Chain Security

## Objective

Phase 7 strengthens the build and deployment supply chain for the
Spring Boot e-commerce backend.

The repository now defines controls for:

- application SBOM generation
- filesystem vulnerability scanning
- container image vulnerability scanning
- immutable container image publishing
- image digest capture
- keyless container signing with GitHub OIDC
- build provenance attestation
- container image SBOM generation
- digest-pinned Kubernetes deployment

## Security model

Container images are treated as immutable artifacts.

Deployments should use:

    registry/repository@sha256:<digest>

instead of mutable tags such as:

    latest

or:

    v1.0.0

Tags remain useful for discovery, but the deployment boundary is the
content digest.

## Signing model

The workflow uses Sigstore Cosign keyless signing.

GitHub Actions provides an OIDC identity token and Cosign signs the
published image without requiring a long-lived private signing key in
repository secrets.

## Provenance

GitHub artifact attestation records provenance for the built image.

This provides evidence connecting the produced image digest with the
GitHub Actions workflow execution that generated it.

## SBOM

Two software bills of materials are generated:

1. Maven/CycloneDX application dependency SBOM.
2. Container image SBOM.

These provide complementary dependency and packaged-image views.

## Vulnerability scanning

Trivy scans:

- the repository filesystem
- the built container image

The workflow blocks on known HIGH or CRITICAL vulnerabilities that have
available fixes.

## Digest deployment

`scripts/deploy_image_digest.sh` rejects mutable image references and
requires an explicit SHA-256 image digest.

The script performs a Kubernetes rollout and verifies that the resulting
deployment references exactly the requested immutable image.

## Boundary

This phase establishes repository and CI/CD supply-chain controls.

It does not by itself prove:

- production registry retention
- production admission-policy enforcement
- organization-wide signing policy
- runtime signature verification
- Kubernetes admission rejection of unsigned images
- cloud workload identity correctness
- production vulnerability remediation SLA
- reproducible builds
- SLSA Build Level 3 compliance
