# Development Journey

This document summarizes how Signature Validator evolved, the assumptions behind it, the most important decisions and blockers, and how the result was tested. It is intended to complement the product-oriented README rather than serve as a detailed implementation specification.

## Starting point

The assignment asked for a convincing click dummy that helps anyone understand a signed or notarized document. I chose to implement the complete upload-to-result flow rather than a set of disconnected screens. The guiding product question was:

> How can a recipient understand what has actually been proven without needing to understand digital-signature infrastructure?

That led to one central design decision: document integrity, embedded signatures, certificate trust, eIDAS qualification, notary-directory matching, and Notarity provenance are presented as separate trust dimensions. A successful result in one dimension never silently implies success in another.

## Product assumptions

- The primary input is a PDF containing zero, one, or multiple embedded PAdES signatures.
- A non-technical user should understand the overall result before opening certificate details.
- Legal and compliance users still need access to signer, certificate, timestamp, and technical evidence.
- Uploaded documents should be processed only for the active request and never permanently stored.
- Missing trust infrastructure is uncertainty, not proof that a document is invalid.
- A Notarity result must apply to the exact document, not merely a matching filename or signer name.
- A company seal is not the same thing as a named civil-law notary.
- Prototype data must remain visibly and structurally separate from real cryptographic evidence.

## How the solution evolved

### 1. End-to-end application skeleton

The first goal was a complete vertical flow:

1. Upload a PDF.
2. Validate its basic structure and size.
3. Calculate its SHA-256 fingerprint.
4. Return a normalized validation response.
5. Render a complete result page.
6. Allow the user to validate another document.

The initial backend used deterministic fixture results behind a replaceable `SignatureValidationService`. This allowed the upload flow, normalized API, result states, accessibility, responsive behavior, and error handling to be developed before real signature validation was introduced.

### 2. Real embedded-signature validation

Fixture signature results were replaced with EU DSS 6.4. The backend now extracts every supported embedded PAdES signature and maps DSS evidence into the existing normalized API instead of exposing DSS objects or XML.

The implementation made these values real:

- signature count and identifiers;
- signature value and signed-content integrity;
- PDF byte-range and revision evidence;
- signer identity and entity type;
- certificate subject, issuer, validity, serial number, and fingerprint;
- signature format and PAdES profile;
- claimed signing time and trusted timestamp evidence.

The fixture implementation was retained for deterministic frontend development and tests.

### 3. Certificate trust and eIDAS qualification

Cryptographic integrity alone cannot establish that a certificate was issued by a trusted qualified service or that a signature is a QES. EU List of Trusted Lists support, national Trusted Lists, certificate-chain building, bounded revocation access, and DSS qualification mapping were therefore added as a separate layer.

The product mapping follows an important rule:

- A failed signature value or signed-content check can make the document invalid.
- Unavailable Trusted Lists, an incomplete chain, or missing historical revocation evidence makes trust indeterminate.
- Trust infrastructure failure does not overwrite successful cryptographic evidence.

This distinction prevents an intact document from being presented as tampered merely because an external trust source is temporarily unavailable.

### 4. Prototype Notarity provenance

The Notarity check was designed as a tamper-evident local prototype rather than a hardcoded success flag.

The registry verifies:

- an exact lowercase SHA-256 document-hash match;
- an Ed25519 signature covering the complete canonical registry payload;
- participant certificate fingerprints against real certificates extracted by DSS;
- the Notarity GmbH seal certificate against the embedded organization signature;
- audit-event integrity as part of the signed payload.

Registry events are labelled as prototype registry evidence. Signature and timestamp events derived from the PDF are labelled as embedded-document evidence. The application does not claim to query Notarity production systems.

### 5. Prototype notary-directory matching

Notary matching is independent from the exact-document Notarity registry. Person signers are normalized and compared with a small static snapshot of public Austrian notarial-signature directory records.

Organizations and electronic seals are excluded from named-notary matching. This matters for the supplied sample: Notarity GmbH is shown as a notarization authority with an embedded electronic seal, not as an individual civil-law notary. The absence of a named notary match is presented neutrally because the static snapshot is deliberately incomplete.

No live directory scraping occurs during validation.

### 6. Verification report

The additional customer-value feature is a downloadable PDF verification report.

Recipients may need to forward validation evidence to a bank, lawyer, compliance team, authority, or business partner without giving that recipient access to the running application. The report provides a portable and printable snapshot of the same result shown in the web interface.

The report does not implement separate validation logic. Because uploaded documents and validation results are not persisted, the report endpoint revalidates the original PDF through the existing service and renders the resulting `DocumentValidationResponse`. This keeps report and UI conclusions aligned.

## Important implementation decisions

### One normalized response

Controllers, UI components, registry checks, and the report renderer consume the normalized application model. DSS-specific objects and raw reports remain inside the backend integration layer.

### Evidence-first status aggregation

Document integrity is derived only from signature value, signed content, byte range, and document-revision evidence. Certificate trust, revocation, qualification, notary matching, and Notarity provenance are aggregated separately.

### Atomic trust-verifier lifecycle

Validation requests must not observe a partially initialized certificate verifier. Trusted List refresh builds a new verifier snapshot and promotes it only when authenticated data is ready. A failed refresh preserves the last usable verifier; when no trusted source is available, validation falls back to stable cryptographic-only processing.

### No database

The application is intentionally stateless. Versioned JSON files are sufficient for the prototype registries, while the persistent Docker volume is used only for authenticated Trusted List cache data.

### Privacy by default

Uploaded bytes remain request-scoped. The application does not log PDF contents, extracted document text, raw DSS XML, full certificate subjects by default, or prototype audit payloads.

### Accessible disclosure

The default result view prioritizes understandable conclusions. Signature details, audit events, warning codes, and technical evidence remain available through keyboard-accessible accordions. Status is communicated with text and icons rather than color alone.

## Main blockers and what changed

### DSS failures lacked a useful stage

Early DSS errors returned a generic HTTP 500 without enough server-side context. Processing-stage tracking was added around document inspection, validator creation, validation, report extraction, result mapping, and response serialization. Safe stage information can be returned in development while full exception chains remain server-side.

### Optional DSS values caused mapper risk

Real signed documents can omit timestamps, certificate tokens, qualification values, issuer fields, and detailed-report nodes that fixtures always supplied. Mapping was made null-safe, and missing optional metadata now produces an absent field or an indeterminate conclusion instead of an HTTP 500.

### Integrity and trust were initially aggregated together

One regression mapped `NO_CERTIFICATE_CHAIN_FOUND` to invalid document integrity even though all signature values and signed content were valid. Aggregation was separated so trust-only uncertainty now produces an overall indeterminate result while document integrity remains valid.

### Trusted List initialization was operationally difficult

Cold synchronization involved the EU LOTL, national Trusted Lists, Official Journal signing certificates, network timeouts, authentication, and persistent cache handling. The Docker cache initially failed because fresh named volumes were owned by root. A small entrypoint now prepares the cache and then launches Java as non-root UID 1001.

Trusted List loading runs outside request processing and exposes explicit refreshing, available, cached, and unavailable states.

### Shared verifier lifecycle caused fragile failure behavior

Mutating a verifier while requests were running risked exposing partial trust configuration. The verifier is now treated as an atomic snapshot. Trust and revocation failures degrade the trust conclusion instead of aborting otherwise successful PDF signature extraction.

### Duplicate-request concern

Concurrent backend logs raised the possibility that the frontend submitted validation more than once. The mutation was configured with retries disabled, submission is ignored while pending, and the validation button remains disabled until the request completes. A frontend test asserts that one submit action produces one API call.

### PDF report layout required rendered inspection

Text-extraction tests alone did not reveal overlap, clipping, poor pagination, or unsupported glyphs. The report was iterated using embedded DejaVu fonts, measured PDFBox layout, explicit page composition, and rendered PNG previews. The supplied three-signature report now fits into two pages without methodology or technical-detail appendices.

### Docker frontend build could not see the notary snapshot

The methodology page imports the real `data/notaries.json` snapshot, but the original frontend Docker context contained only `frontend/`. The build context was moved to the repository root and restricted with `.dockerignore`, allowing Docker to include the snapshot without copying dependencies, build output, cache data, or sample documents.

## Testing approach

### Backend

JUnit 5, MockMvc, Spring Boot integration tests, PDFBox, and small generated signed-PDF fixtures cover:

- missing, oversized, unsupported, malformed, and encrypted uploads;
- PDF page inspection and deterministic SHA-256 output;
- unsigned, valid, modified, and corrupted signed documents;
- signature counts, signer names, certificate fingerprints, and time separation;
- integrity, trust, revocation, and qualification aggregation;
- unavailable and cached trust infrastructure;
- fixture and DSS validation modes;
- notary identity matching and snapshot staleness;
- Ed25519 receipt verification and tampering scenarios;
- participant and Notarity seal fingerprint matching;
- report contents, pagination, embedded fonts, and rendered previews;
- health, capabilities, and structured error responses.

Automated trust tests use local deterministic evidence rather than depending on live EU endpoints.

### Frontend

Vitest and React Testing Library cover:

- file selection, type and size validation, removal, and replacement;
- processing and API-error states;
- one request per submit action;
- verified, invalid, indeterminate, and unsigned results;
- person, organization, signature, and seal presentation;
- friendly DSS warning mapping and deduplication;
- independent trust-summary aggregation;
- exact-match, no-record, receipt-invalid, and signer-mismatch registry states;
- notary snapshot results and limitations;
- accessible signature, audit, methodology, and technical-detail accordions;
- report download loading, success, and failure behavior;
- public footer content and responsive presentation rules.

### Manual checks

- Docker Compose cold start and restart
- Trusted List cache permissions under UID 1001
- Single-request reproduction with `curl`
- Sample validation against the DSS demonstration service where appropriate and non-sensitive
- Desktop and mobile result flows
- PDF report rendering to PNG for visual inspection

## Tools used

- **Codex / OpenAI** — implementation assistance, debugging, code review, test generation, UX copy refinement, and documentation. AI output was treated as a working draft and verified through compilation, automated tests, runtime logs, and rendered previews.
- **React, TypeScript, Vite** — frontend application and production build.
- **TanStack Query and Zod** — mutation lifecycle and runtime response validation.
- **Java 21 and Spring Boot** — backend API, configuration, validation, and integration testing.
- **EU DSS 6.4** — PAdES extraction, signature validation, trust evaluation, revocation evidence, and eIDAS qualification.
- **Apache PDFBox** — PDF inspection, generated test documents, report generation, text extraction, and rendered preview checks.
- **Docker and Docker Compose** — repeatable frontend/backend execution and persistent trust cache.
- **JUnit 5, MockMvc, Vitest, and React Testing Library** — automated verification.
- **curl and application logs** — deterministic API reproduction and stage-level diagnosis.
- **Git and GitHub** — version control and solution delivery.

## Known flaws and limitations

- The first authenticated Trusted List synchronization can be slow and depends on external EU and national services.
- Historical revocation can remain indeterminate when neither embedded nor reachable evidence is sufficient.
- The static Austrian notary snapshot contains only a few records and may become stale.
- The supplied sample exposes a Notarity GmbH organization seal but no named civil-law notary certificate; the application intentionally does not invent one.
- The prototype Notarity registry proves the integrity of its local signed record, not the operational truth of a production Notarity event.
- There is no result history because documents and responses are not persisted.
- The interface and report currently use English.
- Only PDF/PAdES is supported; XAdES and CAdES remain future formats.
- The project implements considerably more than the requested click dummy, increasing setup and operational complexity.

## What I would improve next

- Add a live, appropriately authorized Austrian notary-directory integration.
- Replace the local Notarity registry with an authenticated production API.
- Digitally sign verification reports and provide QR-code report verification.
- Add XAdES and CAdES validation.
- Add multilingual UI and report generation.
- Add an explicitly distributable demo document for every major state.
- Deploy a public demonstration environment with monitored trust-cache readiness.

## Final reflection

The most important product lesson was that “valid” is not one question. A PDF can be cryptographically intact while certificate trust is unavailable; a trusted signature can have no notary-directory match; and an exact prototype Notarity record can be valid without exposing a named civil-law notary in the document.

The final interface is structured around those distinctions. It leads with a clear outcome, preserves inspectable evidence, and communicates prototype boundaries without hiding the parts that are genuinely validated.
