import { useId, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import notarySnapshot from '../../../data/notaries.json'

const coreFunctionality = [
  ['PDF upload and parsing', 'Implemented'],
  ['SHA-256 document fingerprint', 'Implemented'],
  ['Embedded signature extraction', 'Implemented'],
  ['Cryptographic signature verification', 'Implemented'],
  ['PAdES profile detection', 'Implemented'],
  ['Certificate chain validation', 'Implemented'],
  ['EU Trusted List verification', 'Implemented'],
  ['Revocation checking', 'Implemented'],
  ['eIDAS qualification', 'Implemented'],
  ['Verification report generation', 'Implemented'],
  ['Prototype Notarity registry', 'Prototype'],
  ['Prototype Austrian notary directory', 'Prototype'],
] as const

const assumptions = [
  'Documents are uploaded as PDF files.',
  'Validation is performed independently for each uploaded document.',
  'Uploaded documents are processed only for validation and are not permanently stored.',
  'Prototype business integrations are intentionally separated from cryptographic validation.',
  'Trust results are always shown independently so that one successful check does not imply another.',
]

const sources = [
  ['EU DSS 6.4', 'Signature parsing and cryptographic validation'],
  ['EU Trusted Lists', 'Certificate trust and eIDAS qualification'],
  ['Static Austrian notary directory snapshot', 'Prototype notary matching'],
  ['Prototype Notarity registry', 'Exact document verification and audit trail'],
] as const

const realValidation = [
  'PDF parsing', 'Embedded signature extraction', 'Signature integrity verification',
  'Certificate chain validation', 'EU Trusted List validation', 'Revocation checking',
  'Timestamp validation', 'eIDAS qualification',
]

const prototypeIntegrations = [
  'Austrian notary directory snapshot', 'Notarity registry', 'Audit trail',
  'Registry receipt verification',
]

const futureImprovements = [
  'Live Austrian notary directory integration', 'Production Notarity registry API',
  'QR-code based report verification', 'Digitally signed verification reports',
  'Support for XAdES and CAdES signatures', 'Multi-language verification reports',
]

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(value))
}

function Checklist({ items }: { items: readonly string[] }) {
  return <ul className="overview-checklist">{items.map((item) => <li key={item}><Check aria-hidden="true" /><span>{item}</span></li>)}</ul>
}

export function MethodologyPage() {
  const [stackOpen, setStackOpen] = useState(false)
  const stackId = useId()

  return <div className="content-page implementation-page"><div className="shell overview-shell">
    <header className="overview-header">
      <h1>Implementation Overview</h1>
      <p className="lead">A summary of the prototype, implementation decisions, and current scope.</p>
    </header>

    <section aria-labelledby="product-overview"><h2 id="product-overview">Product Overview</h2><p><strong>Signature Validator</strong> is a prototype that helps users understand the trust status of digitally signed PDF documents. It combines real cryptographic validation with prototype business integrations to present technical verification results in a clear, human-readable format.</p></section>

    <section aria-labelledby="core-functionality"><h2 id="core-functionality">Core Functionality</h2><div className="overview-table-wrap"><table className="overview-table"><thead><tr><th>Component</th><th>Status</th></tr></thead><tbody>{coreFunctionality.map(([component, status]) => <tr key={component}><td>{component}</td><td><span className={`capability-status ${status === 'Prototype' ? 'is-prototype' : ''}`}><Check aria-hidden="true" />{status}</span></td></tr>)}</tbody></table></div></section>

    <section aria-labelledby="design-assumptions"><h2 id="design-assumptions">Design Assumptions</h2><p><strong>This prototype was intentionally designed around the following assumptions:</strong></p><Checklist items={assumptions} /></section>

    <section aria-labelledby="data-sources"><h2 id="data-sources">Data Sources</h2><div className="overview-table-wrap"><table className="overview-table"><thead><tr><th>Source</th><th>Purpose</th></tr></thead><tbody>{sources.map(([source, purpose]) => <tr key={source}><td><strong>{source}</strong></td><td>{purpose}</td></tr>)}</tbody></table></div><p className="overview-note">The Austrian notary directory uses a <strong>static public directory snapshot</strong> and is intended only to demonstrate the matching workflow. It is not a live registry integration.</p></section>

    <section aria-labelledby="notary-snapshot"><div className="overview-section-heading"><div><h2 id="notary-snapshot">Static Austrian Notary Snapshot</h2><p>Current records used by the application for prototype identity matching.</p></div><span>{notarySnapshot.dataset.recordCount} records · Retrieved {formatDate(notarySnapshot.dataset.retrievedAt)}</span></div>{notarySnapshot.notaries.length ? <div className="overview-table-wrap"><table className="overview-table snapshot-table"><thead><tr><th>Name</th><th>Country</th><th>Registry Status</th><th>Authorization</th><th>Snapshot Retrieved</th></tr></thead><tbody>{notarySnapshot.notaries.map((notary) => <tr key={notary.id}><td><strong>{notary.fullName}</strong></td><td>{notary.countryCode}</td><td>{notary.registryStatus.charAt(0) + notary.registryStatus.slice(1).toLowerCase()}</td><td>{notary.authorizations.length ? 'Recorded' : 'Not provided'}</td><td>{formatDate(notary.sourceReference.retrievedAt)}</td></tr>)}</tbody></table></div> : <p className="overview-empty">No snapshot data available.</p>}<p className="overview-note">The snapshot contains only a small curated subset of publicly available records and is intended solely to demonstrate the matching workflow.</p></section>

    <section className="overview-accordion" aria-labelledby="technical-stack-title"><button type="button" aria-expanded={stackOpen} aria-controls={stackId} onClick={() => setStackOpen(!stackOpen)}><span><strong id="technical-stack-title">Technical Stack</strong><small>Technologies used to build and run Signature Validator</small></span><ChevronDown aria-hidden="true" /></button>{stackOpen && <div id={stackId} className="stack-grid"><div><h3>Frontend</h3><p>React · TypeScript · Vite</p></div><div><h3>Backend</h3><p>Java 21 · Spring Boot</p></div><div><h3>Validation</h3><p>EU DSS 6.4 · Apache PDFBox</p></div><div><h3>Cryptography</h3><p>SHA-256 · Ed25519</p></div><div><h3>Infrastructure</h3><p>Docker · Docker Compose</p></div></div>}</section>

    <section aria-labelledby="scope"><h2 id="scope">Scope</h2><div className="overview-scope-grid"><article><h3>Real Validation</h3><Checklist items={realValidation} /></article><article><h3>Prototype Integrations</h3><Checklist items={prototypeIntegrations} /></article></div><p className="overview-note">Prototype integrations demonstrate the intended product workflow while remaining clearly separated from cryptographic validation.</p></section>

    <section aria-labelledby="future-improvements"><h2 id="future-improvements">Future Improvements</h2><Checklist items={futureImprovements} /></section>

  </div></div>
}
