# Austrian notary directory snapshot

The validator uses a small local snapshot derived from the publicly accessible [Austrian electronic notarial-signature directory](https://www.notar-signaturverzeichnis.at/), which is operated by the Austrian Chamber of Civil Law Notaries under § 140b of the Austrian Notarial Code.

## Collection decision

The public search page is accessible without authentication, but it is a stateful ASP.NET form. On 2 August 2026, `/robots.txt` returned an application error page rather than a machine-readable policy, and the search surface did not provide clear terms authorizing automated extraction. Automated collection is therefore disabled by default. The committed dataset was produced through the curated manual-import workflow. No CAPTCHA, access control, proxy rotation, browser impersonation, or bulk crawling is used.

The five curated searches are listed in `scripts/notary-directory/input/notary-searches.json`. Manually observed public fields are entered in `manual-records.json`, then normalized with:

```bash
node scripts/notary-directory/create-snapshot.mjs --mode manual
```

`--mode scrape` fails closed until `automatedCollectionApproved` is deliberately changed after source permission is established. Both modes share the same normalization and validation pipeline.

## Data retained

The snapshot retains public display name, given name, surname, professional title, country, N-code when shown, signature authorization type and dates when shown, public certificate references when explicitly available, source reference, and retrieval time. Missing source values remain `null` or empty; they are not inferred.

Email addresses, phone numbers, residential/office addresses, birth dates, photos, and unrelated profile information are excluded. Raw downloaded HTML is not committed, apart from a small synthetic parser fixture used by automated tests.

## Integrity and lifecycle

Stable IDs use the public N-code when available. Otherwise they use a deterministic SHA-256 digest of normalized name, country, and source ID. The generator validates metadata, names, IDs, authorization dates, duplicates, and record counts before atomically replacing `data/notaries.json`. A failed generation leaves the previous snapshot intact. `data/notaries.snapshot.json` records generator provenance and warnings.

Snapshots older than `app.notary-registry.stale-after-days` (30 by default) remain usable but emit `NOTARY_SNAPSHOT_STALE`; notary-registry health becomes `DEGRADED`.

## Matching rules

Only `PERSON` certificate signers are checked. A confirmed match requires exact normalized identity, matching country, an authorization period covering the best available signing date, and no conflicting record. A matching identity with missing date evidence is only a possible match. Dates outside the authorization period do not match. Multiple equally plausible entries are ambiguous. Certificate references, when present in both sources, must match as normalized SHA-256 values.

Notary matching is independent of the prototype Notarity document registry, its transaction ID, document hash, and audit trail.

## Limitations and production migration

This is a static public-directory snapshot with deliberately limited coverage. It is not live, complete, real-time, or an official API integration. Absence is neutral and does not establish that someone is not a notary. Users receive an official-directory link for current manual verification.

A production implementation should use an authorized API or formally approved export, signed source snapshots, documented retention, scheduled and monitored refresh, complete coverage, and source-specific change review. End-user validation must still never trigger scraping.
