# Trust validation

Phase 3B uses European Commission DSS 6.4 for cryptographic, certificate-chain,
revocation, trust-service, and signature-qualification conclusions.

## Trusted Lists

The configured entry point is the European Commission List of Trusted Lists
(LOTL), `https://ec.europa.eu/tools/lotl/eu-lotl.xml`. Its signing certificates
are taken from Official Journal notice C/2026/1944. The application pins their
SHA-256 fingerprints and accepts the LOTL only when DSS verifies its XML
signature. DSS follows the LOTL pointers and parses the national TLv5/TLv6
lists; application code does not parse Trusted List XML.

`TrustedListLoadingService` refreshes the shared
`TrustedListsCertificateSource` outside validation requests. Invalid or expired
LOTL/national-list signatures are rejected. A disk cache preserves downloaded
material across restarts and is backed by the `trusted-list-cache` Docker
volume. A failed refresh never replaces the last successfully synchronized
certificate source.

States are `AVAILABLE`, `AVAILABLE_FROM_CACHE`, `REFRESHING`, `UNAVAILABLE`,
`INVALID`, and `NOT_CONFIGURED`. Trust-infrastructure failure makes trust
conclusions indeterminate; it does not make intact signatures invalid.

## Chain and revocation

One immutable, configured `CommonCertificateVerifier` bean is shared by
document validators. DSS combines PDF-embedded certificates and revocation
evidence with authenticated trust anchors. When enabled, bounded online OCSP
and CRL sources use 3-second connection and 5-second response/socket timeouts.
Network failure maps to unknown/indeterminate evidence, never revoked.

The relevant time prefers a valid trusted signature timestamp. A claimed PDF
signing time remains informational and is never promoted to trusted time.
Without historical proof, validation time is used and the limitation remains
visible.

## Qualification

Qualification is mapped only from DSS `SignatureQualification`:
QES/QESeal, advanced signature/seal with a qualified certificate, advanced
signature/seal, basic, or unknown. Names, issuer branding, timestamps, and
PAdES profiles are never used as shortcuts to a qualified conclusion. A QES or
qualified seal therefore requires the certificate, trust-service, and QSCD
evidence DSS expects.

## Failure and privacy behavior

LOTL refreshes and certificate endpoint access have bounded timeouts and never
run once per uploaded document. Health and capabilities expose sanitized state
and timestamps, but not internal URLs or cache paths. Uploaded PDFs remain
in-memory and unretained. Logs exclude PDF content, full certificate subjects,
raw DSS reports, and personal signer details.

## Profiles

- Default: authenticated EU Trusted Lists and bounded online OCSP/CRL.
- `offline`: cryptographic validation only; trust is explicitly indeterminate.
- `test`: deterministic offline behavior with no external network dependency.

Automated tests must use local/generated certificates and mocked trust
conclusions. They must never depend on live EU or revocation endpoints.
