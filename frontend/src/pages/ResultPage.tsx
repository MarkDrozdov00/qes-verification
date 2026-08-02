import { Navigate } from 'react-router-dom'
import { OverallStatusHero } from '../features/validation/OverallStatusHero'
import { TrustSummaryGrid } from '../features/validation/TrustSummaryGrid'
import { SignatureCard } from '../features/validation/SignatureCard'
import { NotaryResultCard } from '../features/validation/NotaryResultCard'
import { NotarityResultCard } from '../features/validation/NotarityResultCard'
import { AuditTimeline } from '../features/validation/AuditTimeline'
import { TechnicalEvidenceAccordion } from '../features/validation/TechnicalEvidenceAccordion'
import { useValidationResult } from '../features/validation/ValidationContext'
export function ResultPage() {
  const { result, sourceFile } = useValidationResult()
  if (!result) return <Navigate to="/" replace state={{ message: 'Validation results are kept only in memory. Upload the document again to continue.' }} />
  return <div className="result-page"><div className="shell result-shell"><OverallStatusHero result={result} sourceFile={sourceFile} /><TrustSummaryGrid result={result} />
    <section><div className="section-heading"><div><h2>Embedded signatures</h2><p>{result.signatures.length ? `${result.signatures.length} signature${result.signatures.length === 1 ? '' : 's'} represented in this result.` : 'No supported embedded signatures were found.'}</p></div></div><div className="signature-list">{result.signatures.map((s) => <SignatureCard signature={s} key={s.id} />)}{!result.signatures.length && <div className="empty-copy">An unsigned document is informational, not automatically invalid or fraudulent.</div>}</div></section>
    <div className="paired-results"><NotarityResultCard result={result.notarityCheck} /><NotaryResultCard result={result.notaryCheck} /></div>
    <AuditTimeline events={result.notarityCheck?.auditTrail ?? []} /><TechnicalEvidenceAccordion result={result} /></div></div>
}
