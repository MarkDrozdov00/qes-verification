import { ChevronDown, CircleCheck, FileSignature, Link2, ShieldAlert } from 'lucide-react'
import { useId, useMemo, useState } from 'react'
import type { AuditTrailEvent } from '../../types/validation'
import { formatDate } from '../../utils/files'

type StageKey = 'SESSION' | 'PARTICIPANT_SIGNING' | 'NOTARITY_COMPLETION' | 'DOCUMENT_REGISTRATION'
export interface AuditStage { key: StageKey; title: string; summary: string; events: AuditTrailEvent[] }

const stageOrder: StageKey[] = ['SESSION', 'PARTICIPANT_SIGNING', 'NOTARITY_COMPLETION', 'DOCUMENT_REGISTRATION']
const stageTitles: Record<StageKey, string> = {
  SESSION: 'Session prepared', PARTICIPANT_SIGNING: 'Participant signatures completed',
  NOTARITY_COMPLETION: 'Notarity completion', DOCUMENT_REGISTRATION: 'Document registered',
}

function stageFor(event: AuditTrailEvent): StageKey {
  const type = event.type.toUpperCase()
  if (type === 'SESSION_CREATED' || type.includes('INVITED') || type.includes('IDENTITY')) return 'SESSION'
  if (type === 'DOCUMENT_REGISTERED' || type.includes('REGISTRATION')) return 'DOCUMENT_REGISTRATION'
  if (type.includes('NOTARY') || type.includes('NOTARITY') || type.includes('SEAL')) return 'NOTARITY_COMPLETION'
  if (type.includes('SIGNED') || type.includes('SIGNATURE')) return 'PARTICIPANT_SIGNING'
  if (event.evidenceSource === 'EMBEDDED_DOCUMENT' && /seal/i.test(event.title)) return 'NOTARITY_COMPLETION'
  if (event.evidenceSource === 'EMBEDDED_DOCUMENT') return 'PARTICIPANT_SIGNING'
  return 'SESSION'
}

const plural = (count: number, singular: string, multiple = `${singular}s`) => `${count} ${count === 1 ? singular : multiple}`

function stageSummary(events: AuditTrailEvent[]) {
  const registry = events.filter((event) => event.evidenceSource !== 'EMBEDDED_DOCUMENT').length
  const embedded = events.length - registry
  return [registry ? plural(registry, 'registry event') : '', embedded ? plural(embedded, 'embedded signature') : ''].filter(Boolean).join(' · ')
}

export function groupAuditEvents(events: AuditTrailEvent[]): AuditStage[] {
  const groups = new Map<StageKey, AuditTrailEvent[]>()
  events.forEach((event) => { const key = stageFor(event); groups.set(key, [...(groups.get(key) ?? []), event]) })
  return stageOrder.flatMap((key) => { const grouped = groups.get(key); return grouped?.length ? [{ key, title: stageTitles[key], summary: stageSummary(grouped), events: grouped }] : [] })
}

function compactTimeRange(events: AuditTrailEvent[]) {
  const first = new Date(events[0].occurredAt), last = new Date(events[events.length - 1].occurredAt)
  const day = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(first)
  const time = (date: Date) => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC' }).format(date)
  return first.getTime() === last.getTime() ? `${day}, ${time(first)} UTC` : `${day}, ${time(first)}–${time(last)} UTC`
}

function stageStatus(events: AuditTrailEvent[]) {
  if (events.some((event) => event.status === 'INVALID')) return { label: 'Evidence invalid', className: 'invalid' }
  if (events.every((event) => event.status === 'VERIFIED')) return { label: 'Verified', className: 'verified' }
  return { label: 'Informational', className: 'informational' }
}

function SourceIcons({ events }: { events: AuditTrailEvent[] }) {
  const embedded = events.some((event) => event.evidenceSource === 'EMBEDDED_DOCUMENT')
  const registry = events.some((event) => event.evidenceSource !== 'EMBEDDED_DOCUMENT')
  return <span className="stage-sources" aria-label={[embedded ? 'Embedded document evidence' : '', registry ? 'Prototype Notarity registry evidence' : ''].filter(Boolean).join(' and ')}>{embedded && <FileSignature aria-hidden="true" />}{registry && <Link2 aria-hidden="true" />}</span>
}

function DetailedTimeline({ events }: { events: AuditTrailEvent[] }) {
  return <ol className="timeline">{events.map((event) => { const embedded = event.evidenceSource === 'EMBEDDED_DOCUMENT'; const Icon = embedded ? FileSignature : Link2; return <li key={event.id} className={embedded ? 'from-document' : 'from-registry'}><span className="timeline-icon"><Icon aria-hidden="true" /></span><div><span className="source-label">{embedded ? 'Embedded document evidence' : 'Prototype Notarity registry evidence'}</span><h3>{event.title}</h3><div className="timeline-meta"><time dateTime={event.occurredAt}>{formatDate(event.occurredAt)}</time>{event.actor?.displayName && <span>Actor: {event.actor.displayName}</span>}</div>{event.description && <p>{event.description}</p>}<span className={`audit-integrity audit-${event.status.toLowerCase()}`}>{event.status === 'VERIFIED' ? 'Verified evidence' : event.status === 'INVALID' ? 'Receipt invalid' : 'Informational'}</span></div></li> })}</ol>
}

export function AuditTimeline({ events }: { events: AuditTrailEvent[] }) {
  const [sectionOpen, setSectionOpen] = useState(false)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const sectionId = useId()
  const detailsId = useId()
  const ordered = useMemo(() => [...events], [events])
  const stages = useMemo(() => groupAuditEvents(ordered), [ordered])
  const sourceCount = new Set(events.map((event) => event.evidenceSource)).size
  const verified = events.every((event) => event.status === 'VERIFIED')
  const summary = events.length
    ? `${events.length} ${verified ? 'verified ' : ''}event${events.length === 1 ? '' : 's'} from ${sourceCount} evidence source${sourceCount === 1 ? '' : 's'}`
    : 'No audit events available'
  return <section className="audit-section">
    <button type="button" className="audit-section-toggle" aria-expanded={sectionOpen} aria-controls={sectionId} onClick={() => setSectionOpen(!sectionOpen)}>
      <span><strong>Audit trail</strong><small>{summary}</small></span><ChevronDown aria-hidden="true" />
    </button>
    {sectionOpen && <div className="audit-section-content" id={sectionId}>
      <p className="audit-intro">Document evidence comes from embedded signatures and timestamps. Registry events come from the signed prototype Notarity receipt.</p>
    {!events.length ? <div className="empty-copy">No audit events are available for this validation.</div> : <>
      <div className="audit-overview"><strong>{events.length} {verified ? 'verified ' : ''}event{events.length === 1 ? '' : 's'} · {sourceCount} evidence source{sourceCount === 1 ? '' : 's'}</strong><time dateTime={`${ordered[0].occurredAt}/${ordered[ordered.length - 1].occurredAt}`}>{compactTimeRange(ordered)}</time></div>
      <div className="audit-stage-rail">{stages.map((stage) => { const status = stageStatus(stage.events); const StatusIcon = status.className === 'invalid' ? ShieldAlert : CircleCheck; return <article className={`audit-stage stage-${status.className}`} key={stage.key}><div className="audit-stage-top"><SourceIcons events={stage.events} /><span>{stage.events.length} event{stage.events.length === 1 ? '' : 's'}</span></div><h3>{stage.title}</h3><p>{stage.summary}</p><time>{compactTimeRange(stage.events)}</time><span className="stage-status"><StatusIcon aria-hidden="true" />{status.label}</span></article> })}</div>
      <button type="button" className="audit-disclosure" aria-expanded={detailsOpen} aria-controls={detailsId} onClick={() => setDetailsOpen(!detailsOpen)}><span>{detailsOpen ? 'Hide audit events' : 'View all audit events'}<small>{events.length} events in chronological order</small></span><ChevronDown aria-hidden="true" /></button>
      {detailsOpen && <div className="audit-details" id={detailsId}><div className="evidence-legend" aria-label="Audit evidence sources"><span><FileSignature aria-hidden="true" />Embedded document evidence</span><span><Link2 aria-hidden="true" />Prototype Notarity registry evidence</span></div><DetailedTimeline events={ordered} /></div>}
    </>}
    </div>}
  </section>
}
