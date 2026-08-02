import { render, screen } from '@testing-library/react'
import { NotaryResultCard } from '../features/validation/NotaryResultCard'
import type { NotaryCheckResult } from '../types/validation'

function result(status: string, stale = false): NotaryCheckResult {
  const confirmed = status === 'CONFIRMED_MATCH' || status === 'POSSIBLE_MATCH'
  return {
    status,
    confidence: confirmed ? 'HIGH' : 'NONE',
    signatureId: 'sig-1',
    searchedIdentity: { displayName: 'Dr. Anna Example', normalizedName: 'anna example', countryCode: 'AT' },
    matchedNotary: confirmed ? {
      id: 'AT-NOTARY-001', fullName: 'Dr. Anna Example', countryCode: 'AT', countryName: 'Austria',
      registryName: 'Austrian notarial-signature directory snapshot', registryStatus: 'ACTIVE',
    } : undefined,
    matchedFields: confirmed ? ['normalizedName', 'countryCode', ...(status === 'CONFIRMED_MATCH' ? ['authorizationPeriod'] : [])] : [],
    registry: { provider: 'Austrian notarial-signature directory snapshot', datasetType: 'STATIC_PUBLIC_SNAPSHOT', updatedAt: '2026-08-02T00:00:00Z', complete: false, recordCount: 5, sourceUrl: 'https://www.notar-signaturverzeichnis.at/', stale },
    explanation: confirmed
      ? 'The signer identity matched the snapshot.'
      : 'No named person signer matched the Austrian notarial-signature directory snapshot.',
    disclaimer: 'This result uses a static, incomplete snapshot of publicly accessible directory records and may not reflect later changes.',
    warnings: stale ? [{ code: 'NOTARY_SNAPSHOT_STALE', severity: 'WARNING', title: 'Notary snapshot may be outdated', message: 'The snapshot is older than the configured threshold.' }] : [],
  }
}

test('renders a confirmed static snapshot match with provenance and official handoff', () => {
  render(<NotaryResultCard result={result('CONFIRMED_MATCH')} />)
  expect(screen.getByText('Austrian notary snapshot match')).toBeInTheDocument()
  expect(screen.getAllByText('Dr. Anna Example')).toHaveLength(2)
  expect(screen.getByText('Austria')).toBeInTheDocument()
  expect(screen.getByText('Name, Country, Authorization period')).toBeInTheDocument()
  expect(screen.getByText(/Retrieved 2 Aug 2026 · Limited coverage/i)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Verify in official directory/i })).toHaveAttribute('rel', 'noopener noreferrer')
  expect(screen.getByText(/static, incomplete snapshot/i)).toBeInTheDocument()
})

test('renders no registry match as a neutral result', () => {
  render(<NotaryResultCard result={result('NO_MATCH')} />)
  expect(screen.getByText('No match in current snapshot')).toBeInTheDocument()
  expect(screen.getByText('No named person signer matched the Austrian notarial-signature directory snapshot.')).toBeInTheDocument()
  expect(screen.queryByText('INVALID')).not.toBeInTheDocument()
})

test('renders possible and stale snapshot states without live-registry claims', () => {
  const { rerender } = render(<NotaryResultCard result={result('POSSIBLE_MATCH')} />)
  expect(screen.getByText('Possible Austrian notary match')).toBeInTheDocument()
  rerender(<NotaryResultCard result={result('CONFIRMED_MATCH', true)} />)
  expect(screen.getByText(/Notary snapshot may be outdated/)).toBeInTheDocument()
  expect(screen.queryByText(/live registry/i)).not.toBeInTheDocument()
})
