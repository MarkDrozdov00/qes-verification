import { Award, BadgeCheck, FileCheck2, FileSignature, Landmark, Link2 } from 'lucide-react'
import type { DocumentValidationResponse, StatusSummary } from '../../types/validation'
import { StatusBadge } from '../../components/StatusBadge'
import { trustSummaryItems } from './resultPresentation'
const icons = [FileCheck2, FileSignature, Award, BadgeCheck, Landmark, Link2]
export function TrustSummaryGrid({ result }: { result: DocumentValidationResponse }) {
  const items = trustSummaryItems(result)
  return <section aria-labelledby="trust-heading"><div className="section-heading"><div><h2 id="trust-heading">Trust summary</h2><p>Each check is shown independently so one positive result does not imply another.</p></div></div>
    <div className="trust-grid">{items.map((item: StatusSummary, index) => { const Icon = icons[index]; const prototype = index === 5 && result.notarityCheck?.status === 'EXACT_MATCH'; return <article className="trust-card" key={item.label}><div className="trust-card-top"><span className="card-icon"><Icon aria-hidden="true" /></span><StatusBadge status={item.status} label={prototype ? 'Prototype verified' : undefined} /></div><h3>{item.label}</h3><strong className="trust-value">{item.value}</strong><p>{item.description}</p></article> })}</div>
  </section>
}
