# Prototype Notarity registry

Phase 4 adds a tamper-evident, local prototype registry. It does not connect to,
represent, or make claims about Notarity production systems.

## What is proven

An uploaded PDF is identified by the SHA-256 digest of its exact bytes. Any byte
change produces a different lookup key. A matching record is useful only after
the application verifies its Ed25519 receipt with a locally trusted public key.
The receipt covers the complete canonical payload: transaction state, document
hash, participants, fingerprints, notary data, timestamps, and audit events.

Participant and notary bindings compare normalized full SHA-256 certificate
fingerprints against certificates extracted from real PDF signatures by DSS.
Names are display-only and never establish a match.

A valid receipt proves that the payload has not changed since the holder of the
prototype private key signed it. Consequently, the audit trail's integrity is
protected as part of that payload. It does not independently prove that an
event occurred in the physical world, that a signer had a legal role, or that a
notary is listed in an authoritative directory.

## Schema and canonicalization

Records use schema version 1 and are stored as:

```json
{
  "payload": {
    "schemaVersion": 1,
    "transactionId": "NOT-DEMO-001",
    "status": "COMPLETED",
    "documentSha256": "...",
    "createdAt": "2026-07-29T13:46:00Z",
    "completedAt": "2026-07-29T14:09:00Z",
    "participants": [],
    "notary": {},
    "auditTrail": []
  },
  "algorithm": "Ed25519",
  "keyId": "prototype-registry-2026-01",
  "signature": "base64..."
}
```

Canonicalization serializes the payload only, with alphabetically ordered
object properties and map keys, stored array order, UTF-8, ISO-8601 timestamps,
all record fields included, and no pretty-print whitespace. This is a
documented prototype convention. A production protocol should use a standard
such as JSON Canonicalization Scheme (RFC 8785).

## Keys

Generate a development key explicitly:

```bash
./scripts/generate-prototype-registry-key
```

The private Ed25519 PKCS#8 key is written to
`local/keys/prototype-registry-private.pem`, permissioned to its owner, and
ignored by Git. Only public keys are committed. Runtime verification resolves
`keyId` exclusively through the trusted local public-key registry. A signed
record can never supply its own trusted key.

The application does not generate keys at startup and does not require a
private key for validation.

## Registering a document

Copy `data/notarization-records/record-template.example.json`, then replace
every participant and notary fingerprint with a real certificate SHA-256 shown
by the current DSS result:

```bash
./scripts/register-prototype-document \
  samples/PoA_signed_sample.pdf \
  data/notarization-records/poa-template.json
```

## Registered PoA demonstration record

The committed prototype dataset contains transaction `NOT-DEMO-POA-001` for
the exact SHA-256 document fingerprint
`8a1fe8ca8dbf5a35505d04bf7fa05b2b0f4b801c27b0b8d3f7a12735f3a677b7`.
Its signed payload binds two Sebastian Wodniansky-Wildenfeld certificate
fingerprints and the Notarity GmbH seal certificate fingerprint extracted by
DSS. The envelope is verified with public key `prototype-registry-2026-01`;
the corresponding development private key is deliberately excluded from Git.

Changing any participant, timestamp, audit event, transaction field, or the
document hash invalidates the Ed25519 receipt. An unrelated document hash does
not select this record even if it contains the same display names.

The command calculates the exact document hash, runs the existing DSS service,
rejects any participant or notary fingerprint absent from the PDF, signs the
canonical payload, writes `records/<sha256>.json`, and updates `index.json`.
It prints the hash, transaction ID, participant count, key ID, and output path.

## Result and tampering behavior

- Valid receipt, exact hash, completed transaction, and all fingerprints:
  `EXACT_MATCH`.
- Unregistered or modified bytes: `NO_RECORD`.
- Index/payload hash inconsistency: `HASH_MISMATCH`.
- Changed signed payload or audit event: `RECEIPT_INVALID`.
- Validly signed record with a missing signer/notary fingerprint:
  `SIGNER_MISMATCH`.
- Unknown key or unsupported algorithm: `INDETERMINATE`.

An invalid prototype receipt never changes a real PDF signature into an invalid
signature. The signature result and prototype provenance result remain separate.
Registry events are labeled `PROTOTYPE_NOTARITY_REGISTRY`; events derived from
DSS PDF evidence are labeled `EMBEDDED_DOCUMENT`.

## Production migration

- JSON files → authenticated registry API.
- Pinned local public keys → managed, authenticated key distribution.
- Local private key → HSM/KMS-backed signing service.
- Mutable static dataset → append-only notarization registry.
- Prototype events → governed production audit-event schema.
- Local file lookup → hash-indexed production datastore.

Production additionally needs key rotation and revocation, authorization,
operational monitoring, durable append-only storage, and independent assurance
over event creation.
