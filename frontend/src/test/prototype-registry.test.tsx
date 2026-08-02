import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditTimeline } from '../features/validation/AuditTimeline'
import { NotarityResultCard } from '../features/validation/NotarityResultCard'
import type { NotarityCheckResult } from '../types/validation'

function registryResult(status: string): NotarityCheckResult {
  const exact = status === 'EXACT_MATCH'
  return {
    status,
    environment: 'PROTOTYPE',
    documentHashMatch: status === 'NO_RECORD' ? false : true,
    registryReceipt: {
      status: exact || status === 'SIGNER_MISMATCH' ? 'VALID' : status === 'RECEIPT_INVALID' ? 'INVALID' : 'INDETERMINATE',
      algorithm: status === 'NO_RECORD' ? undefined : 'Ed25519',
      keyId: status === 'NO_RECORD' ? undefined : 'prototype-registry-2026-01',
    },
    transaction: status === 'NO_RECORD' ? undefined : { id: 'NOT-DEMO-001', status: 'COMPLETED' },
    participantMatches: status === 'NO_RECORD' ? [] : [{
      role: 'PARTY', registryDisplayName: 'Registered participant',
      signatureId: exact ? 'sig-1' : undefined, certificateFingerprintMatch: exact,
      status: exact ? 'MATCHED' : 'NOT_FOUND',
    }],
    notarizationAuthority: status === 'NO_RECORD' ? undefined : {
      name: 'Notarity GmbH', entityType: 'ORGANIZATION', signatureId: 'sig-3',
      certificateFingerprintMatch: exact, qualification: 'Qualified electronic seal',
      trustProvider: 'PrimeSign', sealTrustedAtSigningTime: exact,
    },
    auditTrail: [],
    prototypeNotice: exact
      ? 'This result uses a cryptographically signed prototype registry record and does not query Notarity production systems.'
      : 'No production Notarity system was queried.',
    warnings: [],
  }
}

test.each([
  ['EXACT_MATCH', 'Prototype Notarity record verified'],
  ['NO_RECORD', 'No Notarity record found'],
  ['RECEIPT_INVALID', 'Receipt invalid'],
  ['SIGNER_MISMATCH', 'Signer mismatch'],
])('renders scoped prototype registry state %s', (status, expected) => {
  render(<NotarityResultCard result={registryResult(status)} />)
  expect(screen.getByText(expected)).toBeInTheDocument()
  expect(screen.getByText(/Notarity production systems|production Notarity system/i)).toBeInTheDocument()
  if (status === 'EXACT_MATCH') {
    expect(screen.getByText('Cryptographically valid')).toBeInTheDocument()
    expect(screen.getByText('Ed25519')).toBeInTheDocument()
    expect(screen.getByText('prototype-registry-2026-01')).toBeInTheDocument()
    expect(screen.getByText('Notarity GmbH')).toBeInTheDocument()
    expect(screen.getByText('Qualified electronic seal')).toBeInTheDocument()
    expect(screen.getByText('PrimeSign')).toBeInTheDocument()
    expect(screen.getByText(/embedded electronic seal matched/i)).toBeInTheDocument()
  }
})

const auditEvents = [
  { id: '1', type: 'SESSION_CREATED', occurredAt: '2026-07-29T13:46:00Z', title: 'Session created', evidenceSource: 'PROTOTYPE_NOTARITY_REGISTRY', status: 'VERIFIED' },
  { id: '2', type: 'IDENTITY_VERIFIED', occurredAt: '2026-07-29T13:50:00Z', title: 'Identity verified', evidenceSource: 'PROTOTYPE_NOTARITY_REGISTRY', status: 'VERIFIED' },
  { id: '3', type: 'PARTICIPANT_SIGNED', occurredAt: '2026-07-29T14:03:00Z', title: 'Participant signed', evidenceSource: 'PROTOTYPE_NOTARITY_REGISTRY', status: 'VERIFIED' },
  { id: '4', type: 'PARTICIPANT_SIGNED', occurredAt: '2026-07-29T14:04:00Z', title: 'Second participant signed', evidenceSource: 'PROTOTYPE_NOTARITY_REGISTRY', status: 'VERIFIED' },
  { id: '5', type: 'OTHER', occurredAt: '2026-07-29T14:03:00Z', title: 'Electronic signature embedded', evidenceSource: 'EMBEDDED_DOCUMENT', status: 'VERIFIED' },
  { id: '6', type: 'OTHER', occurredAt: '2026-07-29T14:04:00Z', title: 'Electronic seal embedded', actor: { displayName: 'Notarity GmbH', role: 'Notarization authority' }, evidenceSource: 'EMBEDDED_DOCUMENT', status: 'VERIFIED' },
  { id: '7', type: 'NOTARITY_SEAL_APPLIED', occurredAt: '2026-07-29T14:08:00Z', title: 'Notarity seal applied', evidenceSource: 'PROTOTYPE_NOTARITY_REGISTRY', status: 'VERIFIED' },
  { id: '8', type: 'DOCUMENT_REGISTERED', occurredAt: '2026-07-29T14:09:00Z', title: 'Document registered', evidenceSource: 'PROTOTYPE_NOTARITY_REGISTRY', status: 'VERIFIED' },
]

test('eight audit events collapse into four compact stages and preserve repeated counts', () => {
  const { container } = render(<AuditTimeline events={auditEvents} />)
  const sectionToggle = screen.getByRole('button', { name: /Audit trail/i })
  expect(sectionToggle).toHaveAttribute('aria-expanded', 'false')
  expect(container.querySelectorAll('.audit-stage')).toHaveLength(0)
  fireEvent.click(sectionToggle)
  expect(sectionToggle).toHaveAttribute('aria-expanded', 'true')
  expect(container.querySelectorAll('.audit-stage')).toHaveLength(4)
  expect(screen.getByText('8 verified events · 2 evidence sources')).toBeInTheDocument()
  expect(screen.getByText('2 registry events · 1 embedded signature')).toBeInTheDocument()
  expect(container.querySelector('.audit-stage-rail')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /View all audit events/i })).toHaveAttribute('aria-expanded', 'false')
  expect(screen.queryByText('Session created')).not.toBeInTheDocument()
})

test('keyboard expansion reveals every original event in its supplied order', async () => {
  const user = userEvent.setup()
  render(<AuditTimeline events={auditEvents} />)
  await user.click(screen.getByRole('button', { name: /Audit trail/i }))
  const button = screen.getByRole('button', { name: /View all audit events/i })
  button.focus()
  await user.keyboard('{Enter}')
  expect(button).toHaveAttribute('aria-expanded', 'true')
  const timeline = document.querySelector<HTMLElement>('.timeline')!
  const titles = within(timeline).getAllByRole('heading', { level: 3 }).map((node) => node.textContent)
  expect(titles).toEqual(auditEvents.map((event) => event.title))
  expect(screen.getAllByText('Embedded document evidence').length).toBeGreaterThan(0)
  expect(screen.getAllByText('Prototype Notarity registry evidence').length).toBeGreaterThan(0)
  expect(screen.getByText('Actor: Notarity GmbH')).toBeInTheDocument()
})

test('invalid registry evidence is not presented as verified', () => {
  render(<AuditTimeline events={auditEvents.map((event) => event.id === '7' ? { ...event, status: 'INVALID' } : event)} />)
  fireEvent.click(screen.getByRole('button', { name: /Audit trail/i }))
  const stage = screen.getByText('Notarity completion').closest<HTMLElement>('.audit-stage')!
  expect(within(stage).getByText('Evidence invalid')).toBeInTheDocument()
  expect(within(stage).queryByText('Verified')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: /View all audit events/i }))
  expect(screen.getByText('Receipt invalid')).toBeInTheDocument()
})
