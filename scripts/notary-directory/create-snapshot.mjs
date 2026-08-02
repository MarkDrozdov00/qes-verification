#!/usr/bin/env node
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { atomicWrite, buildSnapshot } from './lib/snapshot-core.mjs'

const directory = resolve(fileURLToPath(new URL('.', import.meta.url)))
const root = resolve(directory, '../..')
const mode = process.argv.includes('--mode') ? process.argv[process.argv.indexOf('--mode') + 1] : 'manual'
if (!['manual', 'scrape'].includes(mode)) throw new Error('Use --mode manual or --mode scrape')
const sourcesConfig = JSON.parse(await readFile(resolve(directory, 'sources.json'), 'utf8'))
const configured = sourcesConfig.sources.find((source) => source.enabled)
const source = { ...configured, baseUrl: process.env.NOTARY_DIRECTORY_SOURCE_URL || configured.defaultBaseUrl }
if (mode === 'scrape' && !source.automatedCollectionApproved) throw new Error('Scrape mode is disabled because automated access permission is not established. Use --mode manual.')
const manual = JSON.parse(await readFile(resolve(directory, 'input/manual-records.json'), 'utf8'))
const records = mode === 'manual' ? manual.records : []
const retrievedAt = manual.retrievedAt || new Date().toISOString()
const snapshot = buildSnapshot({ source, retrievedAt, records, collectionMethod: mode === 'manual' ? 'CURATED_MANUAL_IMPORT' : 'CURATED_AUTOMATED_SEARCH' })
const metadata = { generatedAt: new Date().toISOString(), generatorVersion: '1.0.0', sourceId: source.id,
  collectionMethod: snapshot.dataset.collectionMethod, requestedSearchCount: records.length,
  successfulSearchCount: records.length, recordCount: records.length, warnings: mode === 'manual' ? ['Manual import used because automated access permission was not established.'] : [] }
await atomicWrite(resolve(root, 'data/notaries.json'), snapshot)
await atomicWrite(resolve(root, 'data/notaries.snapshot.json'), metadata)
console.log(`Notary snapshot written: records=${records.length} method=${snapshot.dataset.collectionMethod}`)
