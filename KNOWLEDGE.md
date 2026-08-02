# Signature Validator — Project Knowledge Base

## 1. Project purpose

Build a modern public web application that allows anyone to upload a PDF and understand its digital trust status.

The application should answer:

1. Does the document contain embedded cryptographic signatures?
2. Has the signed document been modified?
3. Who signed it and when?
4. What type and level of signature is present?
5. Is the signer certificate trusted?
6. Does the signer match a registered notary?
7. Does the exact document match a prototype Notarity notarization record?
8. Can the user export a clear verification report?

This is a take-home assignment for Notarity. The implementation should demonstrate both strong product thinking and technical ownership.

---

## 2. Product principles

### Trust must be understandable

Do not expose only raw certificate data. Translate technical validation results into clear language for non-technical users.

### Never overstate certainty

Keep these concepts separate:

* document integrity;
* cryptographic signature validity;
* certificate trust;
* signature qualification;
* notary registry match;
* Notarity record match.

A valid signature does not automatically mean the document was notarized on Notarity.

### Privacy by default

Uploaded documents should be processed temporarily and should not be permanently retained.

The interface should communicate this clearly.

### Technical details remain available

Show a simple summary first, with expandable evidence for legal, compliance, and technical users.

---

## 3. Target users

### Primary

A person who received a digitally signed or notarized document and wants to know whether it can be trusted.

### Secondary

* Legal professionals
* Compliance teams
* Banks and financial institutions
* Public authorities
* Business partners
* Notaries
* Notarity customers

---

## 4. Supported scope

### Document support

Initial version supports:

* PDF documents
* Embedded PAdES signatures
* Multiple signatures in one PDF
* Maximum file size defined by the application

Other file and signature formats are future improvements.

### Real functionality

Implement as genuinely working:

* PDF upload
* File validation
* SHA-256 document fingerprint
* Embedded signature detection
* Cryptographic document-integrity validation
* Signer and certificate metadata extraction
* Certificate-chain validation
* Trusted timestamp inspection
* EU Trusted List-based trust evaluation
* QES, AdES and unknown qualification classification
* PAdES level detection where available
* Multiple-signature results
* Exact document-hash matching
* Certificate fingerprint matching
* Signed prototype registry-record verification
* Verification report export

### Prototype or limited functionality

Implement honestly as prototype functionality:

* Notary registry coverage
* Notarity production-record lookup
* Notarity production audit trail
* Signing-channel attribution such as ID Austria

The underlying matching and verification logic should work, but the available datasets are intentionally limited.

---

## 5. Core user flow

### Step 1 — Upload

The user opens the validator and sees:

* clear product heading;
* short explanation;
* drag-and-drop upload area;
* file requirements;
* privacy statement;
* “Try sample document” option.

Suggested copy:

**Verify a signed or notarized document**

Upload a PDF to inspect its embedded signatures, certificate trust, notary information, and available Notarity evidence.

### Step 2 — Processing

Show meaningful validation stages:

1. Reading document
2. Inspecting embedded signatures
3. Verifying document integrity
4. Checking signer certificates
5. Checking notary information
6. Looking for a Notarity record

Do not use a fake long delay. Progress may complete quickly.

### Step 3 — Result

Display:

1. Overall trust status
2. Plain-language result explanation
3. Trust-summary cards
4. Embedded signatures
5. Notary registry result
6. Notarity record and audit trail
7. Technical evidence
8. Download report action
9. Validate another document action

---

## 6. Overall document statuses

Use five primary states:

### Verified

All important cryptographic checks passed and no material warning was found.

### Verified with warnings

Core signature integrity is valid, but one or more checks are incomplete, unavailable, expired today, or otherwise require attention.

### Invalid

A material cryptographic check failed, such as signed-content modification or an invalid signature.

### Unable to determine

The validator could not reach a reliable conclusion because of unsupported data, missing revocation information, network failure, or another technical limitation.

### Unsigned

No supported embedded cryptographic signatures were found.

Do not describe an unsigned document as fraudulent or invalid.

---

## 7. Trust dimensions

Always present these separately.

### Document integrity

Possible values:

* Valid
* Invalid
* Unable to determine
* Not signed

Meaning:

Whether the signed document bytes still match the embedded signature.

### Embedded signatures

Possible values:

* Number of signatures found
* No signatures found
* Unsupported signature structure

### Signature qualification

Possible values:

* Qualified electronic signature
* Advanced electronic signature with qualified certificate
* Advanced electronic signature
* Electronic signature
* Unable to determine

Avoid using only unexplained abbreviations such as QES or AdES.

### Certificate trust

Possible values:

* Trusted at signing time
* Not trusted
* Revoked
* Expired before signing
* Currently expired but valid at signing
* Unable to determine

### Notary registry

Possible values:

* Confirmed directory match
* Possible directory match
* No match
* Not checked
* Registry unavailable

### Notarity record

Possible values:

* Exact document match
* No record found
* Registry receipt invalid
* Record found but signer mismatch
* Prototype check unavailable

---

## 8. Result-page structure

### A. Overall result header

Example:

**Document verified**

The document has not been modified since signing. Two valid cryptographic signatures were found, including one qualified electronic signature.

If applicable:

An exact matching prototype Notarity notarization record was also verified.

### B. Trust summary

Show six compact cards:

* Document integrity
* Signatures
* Signature level
* Certificate trust
* Notary
* Notarity record

Each card contains:

* status icon;
* short label;
* result;
* optional one-line explanation.

### C. Embedded signatures

Display one expandable card per signature.

Collapsed information:

* signer name;
* signature level;
* validation status;
* signing date.

Expanded information:

* signer certificate subject;
* certificate issuer;
* certificate validity;
* certificate fingerprint;
* signature format;
* PAdES level;
* claimed signing time;
* trusted signing time;
* timestamp result;
* trust-service provider;
* warnings.

### D. Notary registry check

Show:

* matched notary name;
* registration country;
* city or jurisdiction;
* registry name;
* registry status;
* match confidence;
* matched identity fields;
* dataset update date.

Use this wording:

**The signer identity matches an active notary-directory entry.**

Do not claim that the certificate alone proves the signer acted in an official notarial capacity.

### E. Notarity record

When a record matches, show:

* “Notarized on Notarity” status;
* exact document fingerprint match;
* registry receipt validity;
* matched signer certificates;
* transaction reference;
* completion date;
* prototype environment label.

Suggested prototype label:

**Prototype registry**

This result demonstrates the intended verification mechanism and does not query Notarity’s production systems.

### F. Audit trail

Display a clear timeline such as:

* Session created
* Participant identified
* Participant signed
* Counterparty signed
* Notary completed notarization
* Final document registered

Clearly distinguish:

**Embedded document evidence**

from:

**Evidence provided by the prototype Notarity registry**

### G. Technical evidence

Include:

* document SHA-256;
* validation timestamp;
* validator version;
* signature identifiers;
* certificate fingerprints;
* registry key ID;
* validation warnings.

Technical evidence should be collapsed by default.

---

## 9. Additional feature — Verification report

The chosen additional feature is a downloadable verification report.

### User value

Users often validate documents because they need to share the result with:

* a bank;
* a lawyer;
* an authority;
* a compliance team;
* a business partner.

The report turns a one-time check into portable verification evidence.

### Report content

Include:

* overall status;
* filename;
* document fingerprint;
* validation date and time;
* signature results;
* signer identities;
* certificate trust;
* signature qualification;
* notary registry result;
* Notarity record result;
* audit-receipt status;
* warnings;
* methodology and limitations.

### Formats

Required:

* Human-readable PDF or print-ready report

Optional:

* Technical JSON report
* Cryptographically signed JSON report

### Disclaimer

Include:

This report records the validation result observed at the stated time. It does not replace the original electronically signed document or constitute legal advice.

---

## 10. Important result rules

### Do not use one global green check for everything

A document may have:

* a valid signature;
* no notary match;
* no Notarity record.

Show each result independently.

### Do not treat current certificate expiry as automatic failure

A signature may remain valid when:

* the certificate was valid at signing time;
* the signing time is supported by a valid timestamp;
* revocation information confirms the certificate was not revoked then.

### Do not confuse claimed and trusted signing time

A PDF may contain a signer-provided time that is not independently trusted.

Display:

* Claimed signing time
* Trusted timestamp

when both exist.

### Do not claim a signing application without evidence

Display separately:

* Trust provider: A-Trust
* Likely signing channel: ID Austria
* Attribution confidence: High, Medium, or Unknown

### Do not equate notary registration with transaction capacity

A directory match means the signer identity matches a registered notary.

It does not by itself prove that the person acted as a notary for the uploaded transaction.

---

## 11. Main error and edge states

Support at minimum:

* unsupported file type;
* file too large;
* unreadable PDF;
* encrypted or password-protected PDF;
* unsigned PDF;
* corrupted signature;
* modified signed PDF;
* valid signature with unavailable revocation data;
* valid signed PDF without Notarity record;
* multiple signatures with mixed results;
* ambiguous notary match;
* invalid prototype registry receipt;
* backend or validation service unavailable.

Every error must explain what happened and what the user can do next.

---

## 12. Privacy and security

### Prototype behavior

* Do not permanently store uploaded PDFs.
* Process documents only for the validation request.
* Do not require an account.
* Avoid logging document contents.
* Avoid returning raw sensitive certificate data unnecessarily.
* Use file-size limits.
* Validate that uploaded files are genuine PDFs.
* Handle malformed files safely.

### Privacy copy

Suggested upload-screen message:

**Your document is processed only for validation and is not permanently stored.**

Do not make stronger security claims unless they are technically implemented.

---

## 13. Data and persistence

A traditional database is not required for the initial version.

Use versioned JSON datasets for:

* prototype notaries;
* prototype Notarity notarization records;
* signing-service attribution rules;
* trusted prototype public keys.

The validator should remain stateless.

Introduce a database only if implementing:

* user accounts;
* persistent verification history;
* shareable hosted reports;
* document registration;
* administrative registry management;
* analytics or background jobs.

---

## 14. Visual direction

Use Impeccable for UI design and refinement.

The application should feel:

* modern;
* premium;
* trustworthy;
* calm;
* clear;
* legally credible;
* approachable rather than governmental.

### Brand direction

Take inspiration from Notarity’s current visual identity without creating a direct copy.

Use:

* deep navy or very dark blue for primary text and key surfaces;
* teal or green-blue as the main trust accent;
* a fresh light green or lime accent used sparingly;
* warm off-white or very light neutral page backgrounds;
* white cards;
* soft neutral borders;
* red only for genuine failures;
* amber for warnings;
* green for successfully verified states.

Before final styling, inspect the current Notarity website and derive final color tokens from its visible brand.

Do not invent or reproduce proprietary brand assets unless they are provided.

### Visual hierarchy

* Large clear result status
* Generous whitespace
* Rounded but not overly playful cards
* Soft shadows
* Thin borders
* Strong typography
* Compact technical metadata
* Clear icons
* Smooth expandable sections
* Subtle transitions

### Avoid

* generic admin-dashboard appearance;
* excessive gradients;
* neon cybersecurity visuals;
* overly technical certificate screens;
* large walls of text;
* too many equal-weight cards;
* using green for uncertain results;
* using red for unsigned documents.

### Responsive behavior

The complete upload and result flow must work on:

* desktop;
* tablet;
* mobile.

On mobile:

* summary cards stack;
* signature cards remain readable;
* technical tables become label-value rows;
* primary report action remains easy to access.

### Accessibility

* Do not communicate status using color alone.
* Use icons and text labels.
* Maintain strong contrast.
* Support keyboard interaction.
* Use visible focus states.
* Use semantic headings.
* Add accessible names to status icons and controls.

---

## 15. Suggested navigation

Keep navigation minimal.

Header:

* Product logo or prototype name
* How it works
* About validation
* Validate document button

The core validator may also work as a focused single-page experience without full navigation.

Footer:

* Privacy
* Methodology
* Limitations
* GitHub repository
* Prototype notice

---

## 16. Suggested technical architecture

Preferred implementation:

* React or Next.js frontend
* Java Spring Boot backend
* European Commission DSS integrated into the backend
* JSON-based prototype registries
* Docker-based local setup

Basic flow:

1. User uploads PDF.
2. Backend validates file.
3. Backend calculates SHA-256.
4. DSS validates embedded signatures.
5. Results are normalized into the product model.
6. Signers are checked against the prototype notary registry.
7. Document hash is checked against prototype Notarity records.
8. Signed registry receipt is verified.
9. Frontend displays results.
10. User may export a verification report.

---

## 17. Implementation boundaries

### Real

* PDF processing
* Signature validation
* Certificate extraction
* eIDAS-oriented trust evaluation
* Document hashing
* Prototype registry matching
* Registry receipt signature verification
* Report generation

### Limited

* Notary directory dataset
* Signing-channel attribution
* Coverage outside supported PDF signature formats

### Simulated data

* Notarity production transactions
* Production audit events
* Worldwide authoritative notary registry access

Every simulated or prototype component must be visibly and accurately labeled.

---

## 18. Initial test scenarios

Prepare these documents or fixtures:

1. Supplied signed sample with matching prototype Notarity record
2. Unsigned PDF
3. Modified version of the supplied PDF
4. Different valid signed PDF without Notarity record
5. PDF with multiple signatures
6. Corrupted PDF
7. Password-protected PDF
8. Prototype registry record with altered audit event
9. Possible but ambiguous notary match
10. Validation result with unavailable revocation data

The main demo should show:

* valid original;
* modified document;
* different valid signed document;
* unsigned document.

---

## 19. Definition of success

The application is successful when a reviewer can:

1. Upload the supplied PDF.
2. Understand the overall trust result immediately.
3. Inspect every embedded signature.
4. Understand why a signature is trusted or not trusted.
5. See whether the signer matches a notary registry entry.
6. See whether the exact document matches a prototype Notarity record.
7. Understand the source of each piece of evidence.
8. Download a clear verification report.
9. Upload a modified or unrelated document and receive a meaningfully different result.
10. Understand which parts are production-like and which are prototype data.

---

## 20. Codex working rules

When implementing:

* Preserve the separation between trust dimensions.
* Never convert indeterminate states into invalid states.
* Never present prototype data as production Notarity data.
* Keep the validator stateless unless persistence is explicitly introduced.
* Keep uploaded PDF contents out of application logs.
* Normalize backend results before exposing them to the frontend.
* Build reusable components for status cards, signature cards, evidence rows, warnings, and timelines.
* Add loading, empty, error, unsupported, and partial-result states.
* Keep implementation decisions documented in the README.
* Prefer a working vertical slice over disconnected screens.
* Use Impeccable to improve design quality after the main product structure is implemented.
* Do not change established scope or terminology without documenting the reason.
