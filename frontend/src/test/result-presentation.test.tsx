import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { TechnicalEvidenceAccordion } from '../features/validation/TechnicalEvidenceAccordion'
import { TrustSummaryGrid } from '../features/validation/TrustSummaryGrid'
import { OverallStatusHero } from '../features/validation/OverallStatusHero'
import { aggregateQualification } from '../features/validation/resultPresentation'
import type { SignatureResult } from '../types/validation'
import { result } from './fixtures'

function signer(qualification: string, entityType: 'PERSON' | 'ORGANIZATION' = 'PERSON'): SignatureResult {
  const qualificationLabel = qualification === 'QES' ? 'Qualified electronic signature'
    : qualification === 'QESEAL' ? 'Qualified electronic seal'
      : qualification === 'ADESEAL_WITH_QC' ? 'Advanced electronic seal with qualified certificate'
        : 'Advanced electronic signature'
  return {
    id: `${entityType}-${qualification}`, index: 1, status: 'VALID',
    summary: { title: 'Valid', description: 'Valid', warnings: [] },
    signer: { displayName: entityType === 'PERSON' ? 'Signer' : 'Organization', certificateSubject: '', entityType },
    signingTime: { source: 'TRUSTED_TIMESTAMP' },
    signature: { qualification, qualificationLabel, integrity: 'VALID', signatureValue: 'VALID' },
    certificate: { currentStatus: 'VALID', statusAtSigningTime: 'VALID', chainStatus: 'TRUSTED', revocationStatus: 'GOOD', warnings: [] },
    timestamps: [], warnings: [],
  }
}

test('three qualified signatures produce a meaningful aggregate', () => {
  expect(aggregateQualification([signer('QES'), signer('QES'), signer('QES')]).value).toBe('3 qualified signatures')
})

test('qualified signatures and seals use entity-aware aggregate wording', () => {
  expect(aggregateQualification([signer('QES'), signer('QES'), signer('QESEAL', 'ORGANIZATION')]).value).toBe('2 qualified signatures and 1 qualified seal')
})

test('mixed qualification levels retain their full user-facing meaning', () => {
  expect(aggregateQualification([signer('QES'), signer('QES'), signer('ADESEAL_WITH_QC', 'ORGANIZATION')]).value)
    .toBe('2 qualified electronic signatures and 1 advanced electronic seal with qualified certificate')
})

test('trust summary renders prototype exact match and real EU trust wording', () => {
  const data = result('VERIFIED')
  data.signatures = [signer('QES'), signer('QES'), signer('QESEAL', 'ORGANIZATION')]
  data.notarityCheck = { status: 'EXACT_MATCH', environment: 'PROTOTYPE', documentHashMatch: true, registryReceipt: { status: 'VALID' }, transaction: { id: 'tx-1', status: 'COMPLETED' }, participantMatches: [], auditTrail: [], warnings: [] }
  render(<TrustSummaryGrid result={data} />)
  expect(screen.getByText('Trusted EU certificate chains')).toBeInTheDocument()
  expect(screen.getByText('Prototype record verified')).toBeInTheDocument()
  expect(screen.getByText('Prototype verified')).toBeInTheDocument()
})

test('fully trusted hero names real checks and prototype registry match', () => {
  const data = result('VERIFIED')
  data.technicalEvidence.validationMode = 'dss'
  data.signatures = [signer('QES'), signer('QES'), signer('QESEAL', 'ORGANIZATION')]
  data.notarityCheck = { status: 'EXACT_MATCH', environment: 'PROTOTYPE', documentHashMatch: true, registryReceipt: { status: 'VALID' }, participantMatches: [], auditTrail: [], warnings: [] }
  render(<MemoryRouter><OverallStatusHero result={data} /></MemoryRouter>)
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('signatures are verified')
  expect(screen.getByText(/certificate-trust, and eIDAS qualification checks/i)).toBeInTheDocument()
  expect(screen.getByText(/signed prototype Notarity registry record/i)).toBeInTheDocument()
})

test('technical details are collapsed and copy actions announce success', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
  const data = result('VERIFIED')
  render(<TechnicalEvidenceAccordion result={data} />)
  const accordion = screen.getByRole('button', { name: /Technical verification details/i })
  expect(accordion).toHaveAttribute('aria-expanded', 'false')
  expect(screen.queryByText('Document', { selector: 'h3' })).not.toBeInTheDocument()
  fireEvent.click(accordion)
  fireEvent.click(screen.getByRole('button', { name: 'Copy document SHA-256' }))
  expect(writeText).toHaveBeenCalledWith(data.document.sha256)
  expect(await screen.findByText('Copied')).toBeInTheDocument()
})
