# Signature Validator — API Contract

## 1. API principles

The frontend must never depend directly on DSS report structures, XML, Java classes, certificate-library objects, or provider-specific response formats.

The backend must normalize all validation information into a stable product API.

Core rules:

* Keep each trust dimension separate.
* Preserve `INDETERMINATE` results.
* Never convert unavailable checks into failures.
* Do not expose prototype data as production data.
* Use ISO 8601 timestamps in UTC.
* Use SHA-256 fingerprints as lowercase hexadecimal strings.
* Return user-friendly summaries together with technical evidence.
* Do not return uploaded document contents.
* Do not persist uploaded PDFs by default.

Base path:

```text
/api/v1
```

---

# 2. Validate document

## Endpoint

```http
POST /api/v1/validations
Content-Type: multipart/form-data
```

## Request

Multipart fields:

| Field                     | Type     | Required | Description          |
| ------------------------- | -------- | -------: | -------------------- |
| `document`                | PDF file |      Yes | Document to validate |
| `includeTechnicalDetails` | boolean  |       No | Defaults to `true`   |
| `locale`                  | string   |       No | Defaults to `en`     |

Example:

```bash
curl -X POST \
  -F "document=@PoA_signed_sample.pdf" \
  -F "includeTechnicalDetails=true" \
  http://localhost:8080/api/v1/validations
```

## Success response

```http
200 OK
Content-Type: application/json
```

```json
{
  "validationId": "val_01JAZ9M4H8C2S1",
  "validatorVersion": "0.1.0",
  "validatedAt": "2026-08-01T12:42:16Z",

  "overallStatus": "VERIFIED",
  "summary": {
    "title": "Document verified",
    "description": "The document has not been modified since signing. Two valid cryptographic signatures were found, including one qualified electronic signature.",
    "warnings": []
  },

  "document": {
    "filename": "PoA_signed_sample.pdf",
    "mediaType": "application/pdf",
    "sizeBytes": 482391,
    "sha256": "31f9a53e412f07ecf8f4f1134ea53b40b7f8a8ef9ef938f729d02777d37c817a",
    "pageCount": 4,
    "encrypted": false,
    "signed": true
  },

  "trustSummary": {
    "documentIntegrity": {
      "status": "VALID",
      "label": "Document integrity",
      "value": "Valid",
      "description": "The signed document content has not been changed."
    },
    "embeddedSignatures": {
      "status": "VALID",
      "label": "Embedded signatures",
      "value": "2 signatures",
      "description": "Two supported cryptographic signatures were found."
    },
    "signatureQualification": {
      "status": "VALID",
      "label": "Signature level",
      "value": "Qualified electronic signature",
      "description": "At least one signature was classified as a qualified electronic signature."
    },
    "certificateTrust": {
      "status": "VALID",
      "label": "Certificate trust",
      "value": "Trusted at signing time",
      "description": "The signer certificate chain was trusted at the relevant signing time."
    },
    "notaryRegistry": {
      "status": "VALID",
      "label": "Notary",
      "value": "Directory match",
      "description": "One signer matched an active prototype notary-directory entry."
    },
    "notarityRecord": {
      "status": "VALID",
      "label": "Notarity record",
      "value": "Exact document match",
      "description": "The document fingerprint matched a signed prototype Notarity registry record."
    }
  },

  "signatures": [],

  "notaryCheck": null,

  "notarityCheck": null,

  "technicalEvidence": {
    "validationEngine": "EU DSS",
    "validationPolicy": "default-eidas-policy",
    "documentHashAlgorithm": "SHA-256",
    "warnings": []
  },

  "capabilities": {
    "reportPdfAvailable": true,
    "technicalJsonAvailable": true
  }
}
```

---

# 3. Overall status

```ts
type OverallValidationStatus =
  | "VERIFIED"
  | "VERIFIED_WITH_WARNINGS"
  | "INVALID"
  | "INDETERMINATE"
  | "UNSIGNED";
```

## Meaning

### `VERIFIED`

All material cryptographic checks passed.

### `VERIFIED_WITH_WARNINGS`

Core integrity and signature checks passed, but one or more non-critical checks were incomplete or require attention.

### `INVALID`

A material cryptographic check failed.

Examples:

* signed content changed;
* signature value invalid;
* certificate revoked at signing time;
* registry receipt invalid.

### `INDETERMINATE`

The validator could not reliably determine the result.

Examples:

* revocation service unavailable;
* unsupported signature profile;
* missing evidence;
* incomplete certificate chain.

### `UNSIGNED`

No supported embedded cryptographic signatures were found.

---

# 4. Shared status model

```ts
type CheckStatus =
  | "VALID"
  | "WARNING"
  | "INVALID"
  | "INDETERMINATE"
  | "NOT_FOUND"
  | "NOT_CHECKED"
  | "NOT_APPLICABLE";
```

Used for trust cards and sub-checks.

```ts
interface StatusSummary {
  status: CheckStatus;
  label: string;
  value: string;
  description?: string;
}
```

---

# 5. Signature result

Each embedded signature must be represented independently.

```ts
interface SignatureResult {
  id: string;
  index: number;

  status:
    | "VALID"
    | "VALID_WITH_WARNINGS"
    | "INVALID"
    | "INDETERMINATE";

  summary: {
    title: string;
    description: string;
    warnings: ValidationMessage[];
  };

  signer: SignerIdentity;

  signingTime: {
    claimedTime?: string;
    trustedTime?: string;
    bestAvailableTime?: string;
    source:
      | "TRUSTED_TIMESTAMP"
      | "CLAIMED_SIGNING_TIME"
      | "VALIDATION_TIME"
      | "UNKNOWN";
  };

  signature: {
    format?: string;
    profile?: string;
    baselineLevel?: "B-B" | "B-T" | "B-LT" | "B-LTA" | "UNKNOWN";
    qualification:
      | "QES"
      | "ADES_WITH_QC"
      | "ADES"
      | "ELECTRONIC_SIGNATURE"
      | "UNKNOWN";
    qualificationLabel: string;
    integrity:
      | "VALID"
      | "INVALID"
      | "INDETERMINATE";
    signatureValue:
      | "VALID"
      | "INVALID"
      | "INDETERMINATE";
  };

  certificate: CertificateResult;

  trustService?: TrustServiceResult;

  timestamps: TimestampResult[];

  providerAttribution?: ProviderAttribution;

  warnings: ValidationMessage[];
}
```

---

# 6. Signer identity

```ts
interface SignerIdentity {
  displayName: string;

  commonName?: string;
  givenName?: string;
  surname?: string;
  organization?: string;
  organizationalUnit?: string;
  countryCode?: string;
  email?: string;

  certificateSubject: string;

  normalizedName?: string;
}
```

Rules:

* `displayName` must always be present.
* Prefer a human-readable name over the full certificate subject.
* Do not infer professional status from the name alone.
* Do not expose sensitive certificate attributes unless needed.

---

# 7. Certificate result

```ts
interface CertificateResult {
  subject: string;
  issuer: string;

  serialNumber: string;
  sha256Fingerprint: string;

  validFrom: string;
  validUntil: string;

  currentStatus:
    | "VALID"
    | "EXPIRED"
    | "NOT_YET_VALID"
    | "REVOKED"
    | "UNKNOWN";

  statusAtSigningTime:
    | "VALID"
    | "EXPIRED"
    | "NOT_YET_VALID"
    | "REVOKED"
    | "UNKNOWN";

  chainStatus:
    | "TRUSTED"
    | "UNTRUSTED"
    | "INCOMPLETE"
    | "UNKNOWN";

  revocationStatus:
    | "GOOD"
    | "REVOKED"
    | "UNKNOWN"
    | "NOT_CHECKED";

  qualifiedCertificate:
    | true
    | false
    | null;

  certificatePolicies: string[];

  qcStatements: string[];

  publicKeyAlgorithm?: string;
  publicKeySize?: number;

  warnings: ValidationMessage[];
}
```

Important:

* `currentStatus` and `statusAtSigningTime` must remain separate.
* An expired certificate today does not automatically invalidate a past signature.
* `qualifiedCertificate: null` means the result could not be determined.

---

# 8. Trust service result

```ts
interface TrustServiceResult {
  providerName?: string;
  serviceName?: string;
  countryCode?: string;

  serviceType?: string;
  serviceStatus?: string;

  qualifiedAtSigningTime:
    | true
    | false
    | null;

  trustedListSource?: string;
}
```

Example:

```json
{
  "providerName": "A-Trust GmbH",
  "serviceName": "Qualified certificate service",
  "countryCode": "AT",
  "qualifiedAtSigningTime": true,
  "trustedListSource": "EU Trusted List"
}
```

---

# 9. Timestamp result

```ts
interface TimestampResult {
  id: string;

  type:
    | "SIGNATURE_TIMESTAMP"
    | "CONTENT_TIMESTAMP"
    | "ARCHIVE_TIMESTAMP"
    | "DOCUMENT_TIMESTAMP"
    | "OTHER";

  time?: string;

  status:
    | "VALID"
    | "INVALID"
    | "INDETERMINATE";

  authority?: string;

  certificateFingerprint?: string;

  warnings: ValidationMessage[];
}
```

---

# 10. Signing-provider attribution

Used for labels such as ID Austria.

```ts
interface ProviderAttribution {
  providerName: string;

  signingChannel?: string;

  confidence:
    | "HIGH"
    | "MEDIUM"
    | "LOW";

  evidence: string[];

  disclaimer?: string;
}
```

Example:

```json
{
  "providerName": "A-Trust GmbH",
  "signingChannel": "ID Austria",
  "confidence": "HIGH",
  "evidence": [
    "Trusted service provider matched A-Trust",
    "Certificate policy matched configured ID Austria rule"
  ],
  "disclaimer": "The certificate confirms the trust provider. The signing interface may be inferred from certificate policy information."
}
```

Do not return `signingChannel` unless there is supporting evidence.

---

# 11. Notary check

```ts
interface NotaryCheckResult {
  status:
    | "CONFIRMED_MATCH"
    | "POSSIBLE_MATCH"
    | "NO_MATCH"
    | "AMBIGUOUS"
    | "NOT_CHECKED"
    | "REGISTRY_UNAVAILABLE";

  confidence:
    | "HIGH"
    | "MEDIUM"
    | "LOW"
    | "NONE";

  signatureId?: string;

  searchedIdentity?: {
    displayName: string;
    normalizedName?: string;
    countryCode?: string;
  };

  matchedNotary?: {
    id: string;
    fullName: string;
    countryCode: string;
    countryName: string;
    city?: string;
    jurisdiction?: string;
    registryName: string;
    registryStatus:
      | "ACTIVE"
      | "INACTIVE"
      | "UNKNOWN";
    sourceUrl?: string;
  };

  matchedFields: string[];

  registry: {
    provider: string;
    datasetType:
      | "PROTOTYPE_SNAPSHOT"
      | "LIVE_DIRECTORY"
      | "MOCK";
    updatedAt?: string;
  };

  explanation: string;

  disclaimer: string;

  warnings: ValidationMessage[];
}
```

Recommended disclaimer:

```text
A directory match indicates that the signer identity matches a registered notary. It does not by itself prove that the signer acted in an official notarial capacity for this transaction.
```

---

# 12. Notarity prototype record check

```ts
interface NotarityCheckResult {
  status:
    | "EXACT_MATCH"
    | "NO_RECORD"
    | "HASH_MISMATCH"
    | "SIGNER_MISMATCH"
    | "RECEIPT_INVALID"
    | "INDETERMINATE"
    | "NOT_CHECKED";

  environment:
    | "PROTOTYPE"
    | "PRODUCTION";

  documentHashMatch: boolean | null;

  registryReceipt: {
    status:
      | "VALID"
      | "INVALID"
      | "INDETERMINATE";

    algorithm?: "Ed25519";

    keyId?: string;

    verifiedAt?: string;
  };

  transaction?: {
    id: string;
    status:
      | "COMPLETED"
      | "CANCELLED"
      | "UNKNOWN";
    createdAt?: string;
    completedAt?: string;
  };

  participantMatches: ParticipantMatch[];

  notary?: {
    name: string;
    registryId?: string;
    countryCode?: string;
    signatureId?: string;
    certificateFingerprintMatch?: boolean;
  };

  notarizationAuthority?: {
    name: string;
    entityType: "ORGANIZATION";
    signatureId?: string;
    certificateFingerprintMatch?: boolean;
    qualification?: string;
    trustProvider?: string;
    sealTrustedAtSigningTime?: boolean | null;
  };

  auditTrail: AuditTrailEvent[];

  prototypeNotice?: string;

  warnings: ValidationMessage[];
}
```

`notary` is reserved for a named person notary. An organization signature or seal, including a Notarity GmbH seal, must be exposed as `notarizationAuthority` and must not create a named-notary directory match.

Prototype notice:

```text
This result uses a signed prototype registry record and does not query Notarity production systems.
```

---

# 13. Participant match

```ts
interface ParticipantMatch {
  role:
    | "PARTY"
    | "NOTARY"
    | "WITNESS"
    | "OTHER";

  registryDisplayName: string;

  signatureId?: string;

  certificateFingerprint?: string;

  certificateFingerprintMatch:
    | true
    | false
    | null;

  signingTime?: string;

  status:
    | "MATCHED"
    | "MISMATCHED"
    | "NOT_FOUND"
    | "INDETERMINATE";
}
```

---

# 14. Audit trail event

```ts
interface AuditTrailEvent {
  id: string;

  type:
    | "SESSION_CREATED"
    | "PARTICIPANT_INVITED"
    | "IDENTITY_VERIFIED"
    | "PARTICIPANT_SIGNED"
    | "NOTARY_JOINED"
    | "NOTARY_COMPLETED"
    | "DOCUMENT_REGISTERED"
    | "OTHER";

  occurredAt: string;

  title: string;

  description?: string;

  actor?: {
    displayName: string;
    role?: string;
  };

  evidenceSource:
    | "EMBEDDED_DOCUMENT"
    | "PROTOTYPE_NOTARITY_REGISTRY";

  status:
    | "VERIFIED"
    | "INFORMATIONAL"
    | "INVALID";
}
```

---

# 15. Validation messages

```ts
interface ValidationMessage {
  code: string;

  severity:
    | "INFO"
    | "WARNING"
    | "ERROR";

  title: string;

  message: string;

  signatureId?: string;

  technicalDetails?: string;
}
```

Example:

```json
{
  "code": "REVOCATION_DATA_UNAVAILABLE",
  "severity": "WARNING",
  "title": "Revocation status unavailable",
  "message": "The signature integrity is valid, but the certificate revocation status could not be confirmed.",
  "signatureId": "sig-1"
}
```

Codes should be stable and usable in tests.

Suggested initial codes:

```text
UNSUPPORTED_FILE_TYPE
FILE_TOO_LARGE
INVALID_PDF
ENCRYPTED_PDF
NO_SIGNATURES_FOUND
SIGNATURE_VALUE_INVALID
DOCUMENT_INTEGRITY_INVALID
CERTIFICATE_CHAIN_UNTRUSTED
CERTIFICATE_REVOKED
CERTIFICATE_EXPIRED_AT_SIGNING
REVOCATION_DATA_UNAVAILABLE
TRUSTED_LIST_UNAVAILABLE
TIMESTAMP_INVALID
TIMESTAMP_UNAVAILABLE
SIGNATURE_FORMAT_UNSUPPORTED
NOTARY_NO_MATCH
NOTARY_MATCH_AMBIGUOUS
NOTARY_REGISTRY_UNAVAILABLE
NOTARITY_RECORD_NOT_FOUND
NOTARITY_DOCUMENT_HASH_MISMATCH
NOTARITY_SIGNER_MISMATCH
NOTARITY_RECEIPT_INVALID
VALIDATION_ENGINE_UNAVAILABLE
```

---

# 16. Unsigned document response

Unsigned PDFs still return `200 OK`.

```json
{
  "validationId": "val_unsigned_001",
  "validatedAt": "2026-08-01T12:42:16Z",
  "overallStatus": "UNSIGNED",

  "summary": {
    "title": "No cryptographic signatures found",
    "description": "The document does not contain a supported embedded cryptographic signature. This does not necessarily mean that the document is fraudulent.",
    "warnings": []
  },

  "document": {
    "filename": "unsigned.pdf",
    "mediaType": "application/pdf",
    "sizeBytes": 38291,
    "sha256": "c9e...",
    "pageCount": 2,
    "encrypted": false,
    "signed": false
  },

  "signatures": [],

  "notaryCheck": {
    "status": "NOT_CHECKED",
    "confidence": "NONE",
    "matchedFields": [],
    "registry": {
      "provider": "Prototype registry",
      "datasetType": "PROTOTYPE_SNAPSHOT"
    },
    "explanation": "No signer identity was available for a notary-directory check.",
    "disclaimer": "A directory match requires an identifiable embedded signer.",
    "warnings": []
  },

  "notarityCheck": {
    "status": "NO_RECORD",
    "environment": "PROTOTYPE",
    "documentHashMatch": false,
    "registryReceipt": {
      "status": "INDETERMINATE"
    },
    "participantMatches": [],
    "auditTrail": [],
    "prototypeNotice": "This result uses a prototype registry.",
    "warnings": []
  }
}
```

---

# 17. Mixed signature results

When a PDF has multiple signatures, one failed signature must not hide valid signatures.

Example:

```json
{
  "overallStatus": "INVALID",
  "summary": {
    "title": "Document contains an invalid signature",
    "description": "Three signatures were found. Two are valid and one failed document-integrity validation.",
    "warnings": []
  },
  "signatures": [
    {
      "id": "sig-1",
      "index": 1,
      "status": "VALID"
    },
    {
      "id": "sig-2",
      "index": 2,
      "status": "VALID"
    },
    {
      "id": "sig-3",
      "index": 3,
      "status": "INVALID"
    }
  ]
}
```

The overall result becomes `INVALID` when at least one material signature-integrity failure affects the current document.

---

# 18. Error response

Use RFC-style structured errors.

```ts
interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  validationId?: string;
  details?: Record<string, unknown>;
}
```

Example:

```http
415 Unsupported Media Type
```

```json
{
  "timestamp": "2026-08-01T12:42:16Z",
  "status": 415,
  "error": "Unsupported Media Type",
  "code": "UNSUPPORTED_FILE_TYPE",
  "message": "Only PDF documents are supported.",
  "path": "/api/v1/validations"
}
```

Recommended status codes:

| Status | Use                                                           |
| -----: | ------------------------------------------------------------- |
|  `200` | Validation completed, including unsigned or invalid documents |
|  `400` | Missing file or malformed request                             |
|  `413` | File too large                                                |
|  `415` | Unsupported file type                                         |
|  `422` | File claims to be PDF but cannot be parsed                    |
|  `500` | Unexpected backend error                                      |
|  `503` | Validation engine or required external service unavailable    |

A cryptographically invalid document is not an HTTP error. Return `200` with `overallStatus: "INVALID"`.

---

# 19. Retrieve validation result

Optional for the first version.

Only implement if validation results are temporarily stored.

```http
GET /api/v1/validations/{validationId}
```

For a fully stateless prototype, this endpoint may be omitted.

---

# 20. Generate PDF report

## Endpoint

```http
POST /api/v1/reports/pdf
Content-Type: application/json
```

## Request

Preferred approach:

```json
{
  "validationId": "val_01JAZ9M4H8C2S1"
}
```

This requires temporary validation-result persistence.

For a stateless version, use:

```http
POST /api/v1/reports/pdf
Content-Type: multipart/form-data
```

Fields:

| Field      | Required |
| ---------- | -------: |
| `document` |      Yes |
| `locale`   |       No |

The backend validates the file again and generates the report from the fresh result.

This is the recommended first implementation because it avoids trusting a frontend-supplied validation result.

## Response

```http
200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="document-verification-report.pdf"
```

The report must include:

* validation timestamp;
* document filename;
* SHA-256 fingerprint;
* overall result;
* signature summaries;
* certificate status;
* notary check;
* Notarity prototype check;
* warnings;
* limitations;
* validator version.

---

# 21. Generate technical JSON report

## Endpoint

```http
POST /api/v1/reports/json
Content-Type: multipart/form-data
```

Fields:

* `document`
* `locale`

## Response

```http
200 OK
Content-Type: application/json
Content-Disposition: attachment; filename="document-verification-report.json"
```

Response shape:

```ts
interface TechnicalVerificationReport {
  schemaVersion: "1.0";
  generatedAt: string;
  validatorVersion: string;
  validation: DocumentValidationResponse;

  reportSignature?: {
    algorithm: "Ed25519";
    keyId: string;
    signature: string;
  };
}
```

The report-signing feature is optional.

---

# 22. Health endpoint

```http
GET /api/v1/health
```

Response:

```json
{
  "status": "UP",
  "version": "0.1.0",
  "components": {
    "application": "UP",
    "dssValidationEngine": "UP",
    "trustedLists": "UP",
    "notaryRegistry": "UP",
    "prototypeNotarityRegistry": "UP"
  }
}
```

Do not expose sensitive configuration or filesystem information.

---

# 23. Capabilities endpoint

Useful for frontend configuration.

```http
GET /api/v1/capabilities
```

Response:

```json
{
  "supportedMediaTypes": [
    "application/pdf"
  ],
  "supportedSignatureFormats": [
    "PAdES"
  ],
  "maximumFileSizeBytes": 26214400,
  "reportFormats": [
    "PDF",
    "JSON"
  ],
  "notaryRegistryCoverage": [
    "AT_PROTOTYPE"
  ],
  "notarityEnvironment": "PROTOTYPE",
  "documentRetention": "NONE"
}
```

---

# 24. Frontend API usage

The frontend should use this flow:

```text
Upload PDF
    ↓
POST /api/v1/validations
    ↓
Render normalized validation result
    ↓
User selects Download report
    ↓
POST /api/v1/reports/pdf with original file
```

For the first version, retain the uploaded `File` object only in frontend memory while the result page is open.

Do not upload the file twice until the user requests the report.

---

# 25. TypeScript root response

```ts
interface DocumentValidationResponse {
  validationId: string;
  validatorVersion: string;
  validatedAt: string;

  overallStatus: OverallValidationStatus;

  summary: {
    title: string;
    description: string;
    warnings: ValidationMessage[];
  };

  document: {
    filename: string;
    mediaType: "application/pdf";
    sizeBytes: number;
    sha256: string;
    pageCount?: number;
    encrypted: boolean;
    signed: boolean;
  };

  trustSummary: {
    documentIntegrity: StatusSummary;
    embeddedSignatures: StatusSummary;
    signatureQualification: StatusSummary;
    certificateTrust: StatusSummary;
    notaryRegistry: StatusSummary;
    notarityRecord: StatusSummary;
  };

  signatures: SignatureResult[];

  notaryCheck: NotaryCheckResult | null;

  notarityCheck: NotarityCheckResult | null;

  technicalEvidence: {
    validationEngine: string;
    validationPolicy?: string;
    documentHashAlgorithm: "SHA-256";
    warnings: ValidationMessage[];
  };

  capabilities: {
    reportPdfAvailable: boolean;
    technicalJsonAvailable: boolean;
  };
}
```

---

# 26. Backend package boundaries

Recommended backend structure:

```text
backend/src/main/java/.../
├── validation/
│   ├── ValidationController
│   ├── ValidationService
│   ├── DocumentValidationResultMapper
│   └── dss/
│       ├── DssValidationService
│       └── DssConfiguration
│
├── document/
│   ├── DocumentInspectionService
│   ├── DocumentHashService
│   └── PdfSecurityService
│
├── notary/
│   ├── NotaryRegistry
│   ├── JsonNotaryRegistry
│   ├── NotaryMatchingService
│   └── NameNormalizationService
│
├── notarity/
│   ├── PrototypeNotarityRegistry
│   ├── RegistryReceiptVerifier
│   └── ParticipantMatchingService
│
├── report/
│   ├── ReportController
│   ├── PdfReportService
│   └── JsonReportService
│
├── api/
│   ├── model/
│   └── error/
│
└── config/
```

---

# 27. Codex implementation order

Implement the API in this order:

1. Create all API DTOs and enums.
2. Add `GET /api/v1/capabilities`.
3. Add `POST /api/v1/validations` with fixture output.
4. Connect frontend upload flow to the fixture endpoint.
5. Add real file validation and SHA-256.
6. Integrate DSS behind `DssValidationService`.
7. Map DSS output into the normalized response.
8. Add JSON notary registry.
9. Add prototype Notarity registry.
10. Add PDF and JSON reports.
11. Add edge-case tests.
12. Replace remaining fixture values.

The frontend should not need structural changes when fixture responses are replaced with real validation results.

---

# 28. Definition of done

The API contract is complete when:

* the frontend can render every defined status without DSS knowledge;
* unsigned and invalid documents return `200`;
* malformed uploads return structured API errors;
* all signatures are returned independently;
* claimed and trusted signing times remain separate;
* current and signing-time certificate statuses remain separate;
* notary matching exposes confidence and evidence;
* prototype Notarity responses are clearly labeled;
* report generation uses backend validation results;
* API models are covered by representative JSON fixtures;
* OpenAPI documentation is generated from the implementation.
