import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { AustrianDirectoryAdapter } from '../lib/austrian-directory-adapter.mjs'
import { atomicWrite, buildSnapshot, stableId, validateSnapshot } from '../lib/snapshot-core.mjs'

const source = { id: 'AT_NOTARIAL_SIGNATURE_DIRECTORY', name: 'Austrian electronic notarial signature directory', countryCode: 'AT', baseUrl: 'https://example.test' }
const record = { fullName: 'Dr. Anna Beispiel', givenName: 'Anna', surname: 'Beispiel', countryCode: 'AT', authorizations: [{ type: 'NOTARIAL_SIGNATURE', validFrom: '2024-01-01', validUntil: null }] }

test('parses the curated search input and local HTML fixture', async () => {
  const searches = JSON.parse(await readFile(fileURLToPath(new URL('../input/notary-searches.json', import.meta.url)), 'utf8'))
  assert.equal(searches.searches.length, 5)
  const html = await readFile(fileURLToPath(new URL('./fixtures/result.html', import.meta.url)), 'utf8')
  assert.equal(new AustrianDirectoryAdapter().parse(html)[0].nCode, 'N123456')
})

test('fails visibly when source selectors change', () => assert.throws(() => new AustrianDirectoryAdapter().parse('<html></html>'), /selector/))
test('stable IDs repeat and prefer a public N-code', () => {
  assert.equal(stableId(record, source.id), stableId(record, source.id))
  assert.equal(stableId({ ...record, nCode: 'n123456' }, source.id), 'AT-NOTARY-N123456')
})
test('manual-import snapshot generates metadata and excludes unnecessary personal fields', () => {
  const snapshot = buildSnapshot({ source, retrievedAt: '2026-08-02T00:00:00Z', records: [record], collectionMethod: 'CURATED_MANUAL_IMPORT' })
  assert.equal(snapshot.dataset.recordCount, 1)
  assert.equal(snapshot.dataset.complete, false)
  assert.equal('email' in snapshot.notaries[0], false)
  assert.equal('phone' in snapshot.notaries[0], false)
  assert.equal('address' in snapshot.notaries[0], false)
})
test('rejects duplicates and invalid authorization ranges', () => {
  const snapshot = buildSnapshot({ source, retrievedAt: '2026-08-02T00:00:00Z', records: [record], collectionMethod: 'CURATED_MANUAL_IMPORT' })
  assert.throws(() => validateSnapshot({ ...snapshot, dataset: { ...snapshot.dataset, recordCount: 2 }, notaries: [snapshot.notaries[0], snapshot.notaries[0]] }), /Duplicate/)
  assert.throws(() => buildSnapshot({ source, retrievedAt: '2026-08-02T00:00:00Z', records: [{ ...record, authorizations: [{ type: 'NOTARIAL_SIGNATURE', validFrom: '2025-01-01', validUntil: '2024-01-01' }] }], collectionMethod: 'CURATED_MANUAL_IMPORT' }), /precedes/)
})
test('atomic replacement preserves the previous file when validation fails', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'notary-snapshot-'))
  const output = join(directory, 'notaries.json')
  await writeFile(output, 'previous', 'utf8')
  assert.throws(() => validateSnapshot({ schemaVersion: 2, dataset: {}, notaries: [] }))
  assert.equal(await readFile(output, 'utf8'), 'previous')
  await atomicWrite(output, { valid: true })
  assert.match(await readFile(output, 'utf8'), /"valid": true/)
})
