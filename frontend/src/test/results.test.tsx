import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { OverallStatusHero } from '../features/validation/OverallStatusHero'
import { SignatureCard } from '../features/validation/SignatureCard'
import type { SignatureResult } from '../types/validation'
import { result } from './fixtures'

function signature(overrides: Partial<SignatureResult> = {}): SignatureResult {
  return {
    id: 'sig-1',
    index: 1,
    status: 'VALID_WITH_WARNINGS',
    summary: { title: 'Valid with warnings', description: 'Cryptographic integrity is valid.', warnings: [] },
    signer: { displayName: 'Sebastian Wodniansky-Wildenfeld', certificateSubject: 'CN=SEBASTIAN WODNIANSKY-WILDENFELD', entityType: 'PERSON' },
    signingTime: { bestAvailableTime: '2026-07-29T14:05:00Z', claimedTime: '2026-07-29T14:05:00Z', source: 'CLAIMED_SIGNING_TIME' },
    signature: { format: 'PAdES', profile: 'PAdES_BASELINE_LT', baselineLevel: 'B-LT', qualification: 'UNKNOWN', qualificationLabel: 'Not evaluated yet', integrity: 'VALID', signatureValue: 'VALID' },
    certificate: {
      subject: 'CN=SEBASTIAN WODNIANSKY-WILDENFELD,O=notarity GmbH,C=AT',
      issuer: 'CN=PrimeSign Qualified Seal CA 2021,O=PrimeSign GmbH,C=AT',
      serialNumber: '123456',
      sha256Fingerprint: 'ac7ff1f8b7e2245d'.repeat(4),
      validFrom: '2025-01-01T00:00:00Z',
      validUntil: '2027-01-01T00:00:00Z',
      currentStatus: 'VALID',
      statusAtSigningTime: 'UNKNOWN',
      chainStatus: 'UNKNOWN',
      revocationStatus: 'NOT_CHECKED',
      certificatePolicies: ['1.2.3.4'],
      qcStatements: [],
      warnings: [],
    },
    timestamps: [],
    warnings: [],
    ...overrides,
  }
}

for (const status of ['VERIFIED', 'UNSIGNED', 'INVALID'] as const) {
  test(`renders ${status} result`, () => {
    render(<MemoryRouter><OverallStatusHero result={result(status)} /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(status === 'VERIFIED' ? 'Document verified' : status === 'UNSIGNED' ? 'No cryptographic signatures' : 'could not be verified')
  })
}

test('signature accordion is keyboard accessible and separates person identity from qualification', () => {
  render(<SignatureCard signature={signature()} />)
  const button = screen.getByRole('button', { name: /Sebastian Wodniansky-Wildenfeld/i })
  expect(button).toHaveAttribute('aria-expanded', 'false')
  expect(button).toHaveTextContent('Person · Electronic signature')
  fireEvent.click(button)
  expect(button).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByText('eIDAS qualification')).toBeInTheDocument()
  expect(screen.getAllByText('Unable to determine').length).toBeGreaterThan(0)
})

test('maps and deduplicates DSS chain warnings while keeping the code collapsed', () => {
  const warning = {
    code: 'DSS_NO_CERTIFICATE_CHAIN_FOUND',
    severity: 'INFO' as const,
    title: 'DSS validation detail',
    message: 'DSS reported no certificate chain found.',
    signatureId: 'sig-1',
    technicalDetails: 'DSS indication=INDETERMINATE; subIndication=NO_CERTIFICATE_CHAIN_FOUND',
  }
  render(<SignatureCard signature={signature({
    summary: { title: 'Warning', description: 'Warning', warnings: [warning] },
    warnings: [warning],
  })} />)
  fireEvent.click(screen.getByRole('button', { name: /Sebastian/i }))

  expect(screen.getAllByText('Certificate chain')).toHaveLength(1)
  expect(screen.getByText(/trusted certificate sources are not configured/i)).toBeInTheDocument()
  expect(screen.queryByText('DSS reported no certificate chain found.')).not.toBeInTheDocument()
  expect(screen.queryByText('NO_CERTIFICATE_CHAIN_FOUND')).not.toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Technical detail' }))
  expect(screen.getByText('NO_CERTIFICATE_CHAIN_FOUND')).toBeInTheDocument()
})

test('shows readable issuer fields and keeps raw certificate data in technical details', () => {
  render(<SignatureCard signature={signature()} />)
  fireEvent.click(screen.getByRole('button', { name: /Sebastian/i }))

  expect(screen.getByText('PrimeSign Qualified Seal CA 2021')).toBeInTheDocument()
  expect(screen.getByText('PrimeSign GmbH')).toBeInTheDocument()
  expect(screen.getByText('Austria')).toBeInTheDocument()
  expect(screen.getByText('Trust status not yet determined')).toBeInTheDocument()
  expect(screen.queryByText(/CN=PrimeSign Qualified/)).not.toBeInTheDocument()
  expect(screen.queryByText(/^UNKNOWN$/)).not.toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: /Technical details/i }))
  expect(screen.getByText('CN=PrimeSign Qualified Seal CA 2021,O=PrimeSign GmbH,C=AT')).toBeInTheDocument()
  expect(screen.getByText(/ac7f f1f8 b7e2 245d/).closest('dd')).toHaveClass('mono')
})

test('labels organization signers as electronic seals without claiming qualification', () => {
  render(<SignatureCard signature={signature({
    signer: { displayName: 'Notarity GmbH', certificateSubject: 'O=notarity GmbH,C=AT', entityType: 'ORGANIZATION' },
  })} />)
  const summary = screen.getByRole('button', { name: /Notarity GmbH/i })
  expect(summary).toHaveTextContent('Organization · Electronic seal')
  expect(summary).not.toHaveTextContent(/qualified/i)
})

test('trust-only indeterminate result keeps cryptographic integrity messaging', () => {
  const dss = result('INDETERMINATE')
  dss.technicalEvidence.validationMode = 'dss'
  dss.technicalEvidence.validationEngine = 'EU DSS 6.4'
  dss.summary = {
    title: 'The document has not been modified since signing',
    description: 'Three embedded signatures passed cryptographic integrity checks. Certificate-chain trust and eIDAS qualification could not be established because authenticated EU Trusted List data was unavailable.',
    warnings: [],
  }
  dss.signatures = Array.from({ length: 3 }, (_, index) => signature({ id: `sig-${index}`, index: index + 1, status: 'INDETERMINATE' }))
  render(<MemoryRouter><OverallStatusHero result={dss} /></MemoryRouter>)
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('cryptographically intact')
  expect(screen.getByText(/Three embedded signatures passed cryptographic integrity checks/i)).toBeInTheDocument()
  expect(screen.queryByText(/integrity failed/i)).not.toBeInTheDocument()
})

test('collapsed signature separates cryptographic validity from trust', () => {
  render(<SignatureCard signature={signature({ status: 'INDETERMINATE' })} />)
  const summary = screen.getByRole('button', { name: /Sebastian/i })
  expect(summary).toHaveTextContent('Valid')
  expect(summary).not.toHaveTextContent('Invalid')
})

test.each([[1, 'One embedded signature passed'], [3, 'Three embedded signatures passed']])(
  'hero uses correct grammar for %i real signatures',
  (count, copy) => {
    const dss = result('VERIFIED_WITH_WARNINGS')
    dss.technicalEvidence.validationMode = 'dss'
    dss.technicalEvidence.validationEngine = 'EU DSS 6.4'
    dss.signatures = Array.from({ length: count }, (_, index) => signature({ id: `sig-${index}`, index: index + 1 }))
    render(<MemoryRouter><OverallStatusHero result={dss} /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('The document is cryptographically intact')
    expect(screen.getByText(new RegExp(copy))).toBeInTheDocument()
    expect(screen.getByText('How this result was verified')).toBeInTheDocument()
  },
)
