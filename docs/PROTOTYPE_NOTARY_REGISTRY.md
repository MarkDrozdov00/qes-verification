# Austrian Prototype Notary Registry

Phase 5 adds a small, versioned local notary directory in `data/notaries.json`.
It is a prototype snapshot, not a live or authoritative Austrian directory.

## Evidence boundary

The matcher receives normalized DSS signer results only. It does not receive or
use the document SHA-256, Notarity transaction ID, registry receipt, or audit
trail. The prototype Notarity registry may designate a signer as a notary for a
transaction, but that designation cannot create a notary-directory match.

Only signers classified as `PERSON` are searched. Organization certificates
and electronic seals are skipped.

## Normalization

Names are compared case-insensitively after:

- Unicode decomposition and diacritic removal;
- removal of common professional titles such as `Dr.` and `Mag.`;
- normalization of hyphens, apostrophes, punctuation, and whitespace;
- locale-independent lowercase conversion.

The original signer and registry names remain unchanged in API responses.

## Confidence and status

- `HIGH` / `CONFIRMED_MATCH`: exact normalized name, matching country, and no
  conflicting optional fields.
- `MEDIUM` / `POSSIBLE_MATCH`: close name with matching country, or an exact
  name where an optional corroborating field is unavailable.
- `LOW` / `POSSIBLE_MATCH`: weak unique name evidence without a conflicting
  country.
- `AMBIGUOUS`: multiple entries have the same best confidence.
- `NO_MATCH`: no compatible registry identity exists.
- `NOT_CHECKED`: there are no embedded person signers.

A country conflict prevents a match. Display names alone never establish that
the signer acted in a notarial capacity for the uploaded transaction.

## Dataset lifecycle

The JSON contains a schema version, dataset timestamp, per-entry registry
status, and source label. Docker mounts the file read-only at
`/app/data/notaries.json`. Updating the prototype snapshot requires review and
a new `updatedAt` value; no scraping or live network lookup occurs.
