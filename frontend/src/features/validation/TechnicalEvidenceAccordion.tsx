import { ChevronDown } from 'lucide-react'
import { useId, useState } from 'react'
import { CopyButton } from '../../components/CopyButton'
import { EvidenceRow } from '../../components/EvidenceRow'
import type { DocumentValidationResponse } from '../../types/validation'
import { formatBytes, formatDate } from '../../utils/files'

const copyValue = (value: string | undefined, label: string) => value
  ? <span className="copy-value"><span>{value}</span><CopyButton value={value} label={label} /></span> : undefined

export function TechnicalEvidenceAccordion({ result }: { result: DocumentValidationResponse }) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const receipt = result.notarityCheck?.registryReceipt
  const transactionId = result.notarityCheck?.transaction?.id
  return <section className="technical"><button aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}><div><strong>Technical verification details</strong><span>Document, validation, trust infrastructure, and prototype registry metadata</span></div><ChevronDown aria-hidden="true" /></button>{open && <div className="technical-groups" id={id}>
    <section><h3>Document</h3><dl><EvidenceRow label="SHA-256" value={copyValue(result.document.sha256, 'document SHA-256')} mono /><EvidenceRow label="Filename" value={result.document.filename} /><EvidenceRow label="File size" value={formatBytes(result.document.sizeBytes)} /><EvidenceRow label="Page count" value={String(result.document.pageCount ?? 'Not available')} /></dl></section>
    <section><h3>Validation</h3><dl><EvidenceRow label="Validation ID" value={copyValue(result.validationId, 'validation ID')} mono /><EvidenceRow label="Validated at" value={formatDate(result.validatedAt)} /><EvidenceRow label="Engine" value={result.technicalEvidence.validationEngine} /><EvidenceRow label="DSS version" value={result.technicalEvidence.dssVersion || result.validatorVersion} /><EvidenceRow label="Execution time" value={result.technicalEvidence.executionTimeMillis !== undefined ? `${result.technicalEvidence.executionTimeMillis} ms` : undefined} /></dl></section>
    <section><h3>Trust infrastructure</h3><dl><EvidenceRow label="Trusted List state" value={result.technicalEvidence.trustedListsState || (result.technicalEvidence.trustedListsConfigured ? 'Available' : 'Not configured')} /><EvidenceRow label="Loaded list count" value={result.technicalEvidence.loadedTrustedListCount !== undefined ? String(result.technicalEvidence.loadedTrustedListCount) : undefined} /><EvidenceRow label="Last refresh" value={result.technicalEvidence.trustedListLastRefresh ? formatDate(result.technicalEvidence.trustedListLastRefresh) : undefined} /><EvidenceRow label="Revocation enabled" value={result.technicalEvidence.revocationCheckingConfigured ? 'Yes' : 'No'} /></dl></section>
    <section><h3>Prototype registry</h3><dl><EvidenceRow label="Transaction ID" value={copyValue(transactionId, 'transaction ID')} mono /><EvidenceRow label="Receipt key ID" value={receipt?.keyId} mono /><EvidenceRow label="Algorithm" value={receipt?.algorithm} /></dl></section>
  </div>}</section>
}
