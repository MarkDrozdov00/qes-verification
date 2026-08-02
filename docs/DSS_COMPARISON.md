# DSS comparison

## Configuration

- Local library: European Commission DSS 6.4
- Validation level: `BASIC_SIGNATURES`
- Trusted Lists: not configured
- OCSP/CRL: not configured
- Input retention: none

## Supplied assignment sample

No redistributable or locally supplied assignment PDF was present in the repository during Phase 3A implementation. It was therefore not uploaded to the European Commission demonstration application and no sensitive document left the local environment.

When an authorized sample is available, compare the following fields:

| Field | EC DSS demonstration | Local validator |
| --- | --- | --- |
| Signature count | Pending authorized sample | Pending authorized sample |
| Signer display names | Pending | Pending |
| Signature formats | Pending | Pending |
| Claimed signing times | Pending | Pending |
| Trusted timestamp times | Pending | Pending |
| DSS indications | Pending | Pending |
| DSS sub-indications | Pending | Pending |
| Signature-value integrity | Pending | Pending |
| Signed-content integrity | Pending | Pending |
| PDF revision conclusions | Pending | Pending |

The EC demonstration service is a manual development comparison tool only. It is not called by the application and must not receive sensitive files without explicit authorization.

## Expected differences

The local Phase 3A validator intentionally lacks Trusted Lists and revocation sources. Its certificate-trust and eIDAS qualification conclusions will therefore be less complete than a fully configured EC demonstration deployment, even when signature counts, signer certificates, formats, timestamps, signature values, and signed-content integrity agree.
