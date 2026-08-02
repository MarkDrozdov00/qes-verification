import { ArrowLeft, Download } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { DocumentValidationResponse } from '../../types/validation'
import { StatusBadge, StatusIcon, statusTone } from '../../components/StatusBadge'
import { ValidationScopeAccordion } from './ValidationScopeAccordion'
import { aggregateCertificateTrust } from './resultPresentation'
import { generatePdfReport, ValidationApiError } from '../../api/validationApi'
import { useState } from 'react'

export function OverallStatusHero({ result, sourceFile = null }: { result: DocumentValidationResponse; sourceFile?: File | null }) {
  const [reportState, setReportState] = useState<'idle' | 'pending' | 'success' | 'error'>('idle')
  const [reportError, setReportError] = useState('')
  async function downloadReport() {
    if (!sourceFile || reportState === 'pending') return
    setReportState('pending'); setReportError('')
    try {
      const { blob, filename } = await generatePdfReport(sourceFile)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 0)
      setReportState('success')
    } catch (error) {
      setReportError(error instanceof ValidationApiError ? error.apiError.message : 'The verification report could not be generated.')
      setReportState('error')
    }
  }
  const realCryptography = result.technicalEvidence.validationMode === 'dss'
  const cryptographicallyValid = realCryptography && result.signatures.length > 0 && result.signatures.every((signature) =>
    signature.signature.signatureValue === 'VALID' && signature.signature.integrity === 'VALID')
  const trust = aggregateCertificateTrust(result.signatures)
  const fullyTrusted = cryptographicallyValid && trust.status === 'VALID'
  const count = result.signatures.length
  const number = count === 1 ? 'One' : count === 2 ? 'Two' : count === 3 ? 'Three' : String(count)
  const exactPrototypeMatch = result.notarityCheck?.status === 'EXACT_MATCH'
  const heading = fullyTrusted ? 'This document’s signatures are verified' : cryptographicallyValid ? 'The document is cryptographically intact' : result.summary.title
  const description = fullyTrusted
    ? `${number} embedded signature${count === 1 ? ' passed' : 's passed'} cryptographic, certificate-trust, and eIDAS qualification checks.${exactPrototypeMatch ? ' The exact document also matches a signed prototype Notarity registry record.' : ' No matching prototype Notarity record was found.'}`
    : cryptographicallyValid
      ? `${number} embedded signature${count === 1 ? ' passed' : 's passed'} cryptographic integrity checks. ${trust.status === 'WARNING' ? 'Certificate trust was established for only some signatures.' : 'Certificate-chain trust or eIDAS qualification could not be fully established.'}`
      : result.summary.description
  return <section className={`result-hero tone-surface-${statusTone(result.overallStatus)}`}>
    <div className="result-hero-main"><div className="result-icon"><StatusIcon status={result.overallStatus} size={30} /></div><div><StatusBadge status={result.overallStatus} /><h1>{heading}</h1><p>{description}</p></div></div>
    <div className="result-actions"><button className="button button-secondary report-button" disabled={!sourceFile || reportState === 'pending'} onClick={downloadReport}><Download size={17} />{reportState === 'pending' ? 'Generating verification report…' : 'Download verification report'}</button><Link className="button button-primary" to="/"><ArrowLeft size={17} />Validate another document</Link></div>
    {reportState === 'success' && <div className="report-feedback report-success" role="status">Verification report generated.</div>}
    {reportState === 'error' && <div className="report-feedback report-error" role="alert">{reportError}</div>}
    <ValidationScopeAccordion fixture={!realCryptography} />
  </section>
}
