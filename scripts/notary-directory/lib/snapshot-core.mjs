import { createHash } from 'node:crypto'
import { rename, writeFile } from 'node:fs/promises'

export const normalizeName = (value) => value.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase()
  .replace(/\b(dr|mag|prof|dipl[- ]?ing)\.?\b/g, ' ').replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ')

export function stableId(record, sourceId) {
  if (record.nCode) return `AT-NOTARY-${record.nCode.toUpperCase()}`
  const key = `${normalizeName(record.fullName)}|${record.countryCode}|${sourceId}`
  return `AT-NOTARY-${createHash('sha256').update(key).digest('hex').slice(0, 12).toUpperCase()}`
}

export function normalizeRecord(record, source, retrievedAt) {
  return {
    id: stableId(record, source.id), fullName: record.fullName, givenName: record.givenName ?? null,
    surname: record.surname ?? null, professionalTitle: record.professionalTitle ?? null,
    normalizedName: normalizeName([record.givenName, record.surname].filter(Boolean).join(' ') || record.fullName),
    countryCode: record.countryCode || source.countryCode, nCode: record.nCode ?? null,
    registryStatus: record.registryStatus ?? 'UNKNOWN', authorizations: record.authorizations ?? [],
    certificateReferences: record.certificateReferences ?? [],
    sourceReference: { sourceRecordUrl: record.sourceRecordUrl ?? null, retrievedAt },
  }
}

export function validateSnapshot(snapshot) {
  if (snapshot.schemaVersion !== 2 || !snapshot.dataset?.sourceName || !snapshot.dataset?.retrievedAt) throw new Error('Snapshot metadata is incomplete')
  if (snapshot.dataset.recordCount !== snapshot.notaries.length) throw new Error('Record count does not match metadata')
  const ids = new Set()
  for (const record of snapshot.notaries) {
    if (!record.fullName || !record.id || !record.normalizedName) throw new Error('Every record requires a display name, stable ID, and normalized name')
    if (ids.has(record.id)) throw new Error(`Duplicate record ID: ${record.id}`)
    ids.add(record.id)
    for (const authorization of record.authorizations) {
      if (authorization.validFrom && Number.isNaN(Date.parse(authorization.validFrom))) throw new Error(`Invalid validFrom for ${record.id}`)
      if (authorization.validUntil && Number.isNaN(Date.parse(authorization.validUntil))) throw new Error(`Invalid validUntil for ${record.id}`)
      if (authorization.validFrom && authorization.validUntil && authorization.validUntil < authorization.validFrom) throw new Error(`Authorization end precedes start for ${record.id}`)
    }
  }
  return snapshot
}

export function buildSnapshot({ source, retrievedAt, records, collectionMethod }) {
  const notaries = records.map((record) => normalizeRecord(record, source, retrievedAt))
  return validateSnapshot({ schemaVersion: 2, dataset: {
    name: 'Austrian notarial-signature directory snapshot', datasetType: 'STATIC_PUBLIC_SNAPSHOT',
    countryCode: 'AT', sourceName: source.name, sourceUrl: source.baseUrl, retrievedAt,
    recordCount: notaries.length, complete: false, collectionMethod,
  }, notaries })
}

export async function atomicWrite(path, value) {
  const temporary = `${path}.tmp-${process.pid}`
  await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
  await rename(temporary, path)
}
