import { ExternalLink, Landmark } from 'lucide-react'
import { EvidenceRow } from '../../components/EvidenceRow'
import { StatusBadge } from '../../components/StatusBadge'
import type { CheckStatus, NotaryCheckResult } from '../../types/validation'

const presentation: Record<string, { tone: CheckStatus; label: string }> = {
  CONFIRMED_MATCH: { tone: 'VALID', label: 'Austrian notary snapshot match' },
  POSSIBLE_MATCH: { tone: 'WARNING', label: 'Possible Austrian notary match' },
  NO_MATCH: { tone: 'NOT_FOUND', label: 'No match in current snapshot' },
  AMBIGUOUS: { tone: 'INDETERMINATE', label: 'Ambiguous match' },
  NOT_CHECKED: { tone: 'NOT_CHECKED', label: 'Not checked' },
  REGISTRY_UNAVAILABLE: { tone: 'INDETERMINATE', label: 'Registry unavailable' },
}

const fieldLabel = (field: string) => ({ normalizedName: 'Name', countryCode: 'Country', organization: 'Organization', authorizationPeriod: 'Authorization period', certificateReference: 'Certificate reference' }[field] ?? field)
const snapshotDate = (value?: string) => value ? new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(value)) : undefined

export function NotaryResultCard({ result }: { result: NotaryCheckResult | null }) {
  const state = result ? (presentation[result.status] ?? presentation.NOT_CHECKED) : null
  const explanation = result?.status === 'CONFIRMED_MATCH'
    ? 'The signer identity matched a public directory record and the recorded authorization covered the signing date.'
    : result?.status === 'NO_MATCH'
      ? 'No named person signer matched the Austrian notarial-signature directory snapshot.'
      : result?.explanation
  return <section className="evidence-section"><div className="section-heading"><div><h2><Landmark aria-hidden="true" />Notary registry result</h2><p>Person-signer identity matching against a static public-directory snapshot.</p></div>{result && state && <StatusBadge status={state.tone} label={state.label} />}</div>
    {!result ? <p className="empty-copy">Notary matching was not performed for this result.</p> : <><div className="snapshot-meta"><span>Static public-directory snapshot</span><span>Retrieved {snapshotDate(result.registry.updatedAt) ?? 'date unavailable'} · Limited coverage</span></div><dl className="evidence-grid"><EvidenceRow label="Searched signer" value={result.searchedIdentity?.displayName} /><EvidenceRow label="Confidence" value={result.confidence === 'NONE' ? 'None' : result.confidence.toLowerCase()} /><EvidenceRow label="Notary name" value={result.matchedNotary?.fullName} /><EvidenceRow label="Country" value={result.matchedNotary?.countryName} /><EvidenceRow label="Registry" value={result.matchedNotary?.registryName || result.registry.provider} /><EvidenceRow label="Registry status" value={result.matchedNotary?.registryStatus?.toLowerCase()} /><EvidenceRow label="Snapshot records" value={result.registry.recordCount?.toString()} /><EvidenceRow label="Matched fields" value={result.matchedFields.length ? result.matchedFields.map(fieldLabel).join(', ') : undefined} /></dl><p className="explanation">{explanation}</p>{result.warnings.map((warning) => <p className="snapshot-warning" key={warning.code}>{warning.title}: {warning.message}</p>)}<p className="disclaimer">{result.disclaimer}</p>{result.registry.sourceUrl && <a className="official-directory-link" href={result.registry.sourceUrl} target="_blank" rel="noopener noreferrer">Verify in official directory <ExternalLink aria-hidden="true" /></a>}</>}
  </section>
}
