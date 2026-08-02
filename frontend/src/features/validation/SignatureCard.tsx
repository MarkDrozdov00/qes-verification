import { Building2, ChevronDown, FileSignature, UserRound } from 'lucide-react'
import { useId, useState } from 'react'
import { EvidenceRow } from '../../components/EvidenceRow'
import { StatusBadge } from '../../components/StatusBadge'
import type { SignatureResult } from '../../types/validation'
import { formatDate } from '../../utils/files'
import { countryName, groupFingerprint, parseDistinguishedName } from './certificateFormatting'
import { certificateTrustLabel, statusLabel } from './statusFormatting'
import { deduplicateValidationMessages } from './validationMessageFormatter'
import { ValidationWarning } from './ValidationWarning'
import { qualificationLabel, cryptographicStatus as getCryptographicStatus } from './resultPresentation'
import { CopyButton } from '../../components/CopyButton'

export function SignatureCard({ signature }: { signature: SignatureResult }) {
  const [open, setOpen] = useState(false)
  const [technicalOpen, setTechnicalOpen] = useState(false)
  const contentId = useId()
  const technicalId = useId()
  const warnings = deduplicateValidationMessages([
    ...signature.summary.warnings,
    ...signature.warnings,
    ...signature.certificate.warnings,
  ])
  const issuer = parseDistinguishedName(signature.certificate.issuer)
  const entityType = signature.signer.entityType ?? 'UNKNOWN'
  const entityLabel = entityType === 'PERSON' ? 'Person' : entityType === 'ORGANIZATION' ? 'Organization' : 'Signer'
  const summaryQualification = signature.signature.qualification === 'UNKNOWN'
    ? entityType === 'PERSON' ? 'Electronic signature' : entityType === 'ORGANIZATION' ? 'Electronic seal' : 'Signature type not determined'
    : qualificationLabel(signature)
  const EntityIcon = entityType === 'PERSON' ? UserRound : entityType === 'ORGANIZATION' ? Building2 : FileSignature
  const cryptographicStatus = getCryptographicStatus(signature)

  return <article className={`signature-card ${open ? 'is-open' : ''}`}>
    <button className="signature-toggle" aria-expanded={open} aria-controls={contentId} onClick={() => setOpen(!open)}>
      <div className="signature-number" aria-hidden="true">{signature.index}</div>
      <div className="signature-primary">
        <strong>{signature.signer.displayName}</strong>
        <span className="entity-label"><EntityIcon aria-hidden="true" />{entityLabel} · {summaryQualification}</span>
      </div>
      <div className="signature-date"><span>Signed</span><strong>{formatDate(signature.signingTime.bestAvailableTime)}</strong></div>
      <div className="signature-statuses"><StatusBadge status={cryptographicStatus} label={cryptographicStatus === 'VALID' ? 'Valid' : cryptographicStatus === 'INVALID' ? 'Invalid' : 'Indeterminate'} /></div>
      <ChevronDown className="chevron" aria-hidden="true" />
    </button>

    {open && <div className="signature-details" id={contentId}>
      <div className="signature-detail-groups"><section><h3>Signature</h3><dl>
        <EvidenceRow label="eIDAS qualification" value={qualificationLabel(signature)} />
        {signature.signature.format && <EvidenceRow label="Format" value={signature.signature.format} />}
        {signature.signature.baselineLevel && signature.signature.baselineLevel !== 'UNKNOWN' && <EvidenceRow label="PAdES level" value={signature.signature.baselineLevel} />}
        <EvidenceRow label="Cryptographic integrity" value={cryptographicStatus === 'VALID' ? 'Valid' : statusLabel(cryptographicStatus)} />
      </dl></section><section><h3>Certificate trust</h3><dl>
        {signature.trustService?.providerName && <EvidenceRow label="Trust service provider" value={signature.trustService.providerName} />}
        {signature.trustService?.serviceName && <EvidenceRow label="Service" value={signature.trustService.serviceName} />}
        {signature.trustService?.countryCode && <EvidenceRow label="Country" value={countryName(signature.trustService.countryCode)} />}
        <EvidenceRow label="Certificate trust" value={certificateTrustLabel(signature.certificate.statusAtSigningTime)} />
        <EvidenceRow label="Chain status" value={signature.certificate.chainStatus === 'UNKNOWN' ? 'Unable to determine' : statusLabel(signature.certificate.chainStatus)} />
        <EvidenceRow label="Revocation" value={signature.certificate.revocationStatus === 'GOOD' ? 'No revocation found' : statusLabel(signature.certificate.revocationStatus)} />
      </dl></section><section><h3>Certificate issuer</h3><dl>
        {issuer?.commonName && <EvidenceRow label="Certificate authority" value={issuer.commonName} />}
        {issuer?.organization && issuer.organization !== issuer.commonName && <EvidenceRow label="Issuer organization" value={issuer.organization} />}
        {issuer?.countryCode && <EvidenceRow label="Issuer country" value={countryName(issuer.countryCode)} />}
        {signature.certificate.validFrom && signature.certificate.validUntil && <EvidenceRow label="Certificate validity period" value={`${formatDate(signature.certificate.validFrom)} – ${formatDate(signature.certificate.validUntil)}`} />}
      </dl></section><section><h3>Time evidence</h3><dl>
        {signature.signingTime.claimedTime && <EvidenceRow label="Claimed signing time" value={formatDate(signature.signingTime.claimedTime)} />}
        {signature.signingTime.trustedTime && <EvidenceRow label="Trusted signing time" value={formatDate(signature.signingTime.trustedTime)} />}
        {signature.timestamps.map((timestamp) => timestamp.time && <EvidenceRow key={timestamp.id} label={timestamp.type === 'SIGNATURE_TIMESTAMP' ? 'Signature timestamp' : statusLabel(timestamp.type)} value={`${statusLabel(timestamp.status)} · ${formatDate(timestamp.time)}${timestamp.authority ? ` · ${timestamp.authority}` : ''}`} />)}
      </dl></section></div>

      {warnings.length > 0 && <div className="warning-list">{warnings.map((warning) =>
        <ValidationWarning warning={warning} key={[warning.code, warning.signatureId, warning.severity, warning.technicalDetails].join('|')} />,
      )}</div>}

      <section className="signature-technical">
        <button type="button" aria-expanded={technicalOpen} aria-controls={technicalId} onClick={() => setTechnicalOpen(!technicalOpen)}>
          <span><strong>Technical details</strong><small>Certificate identifiers and DSS evidence</small></span>
          <ChevronDown aria-hidden="true" />
        </button>
        {technicalOpen && <dl id={technicalId}>
          <EvidenceRow label="Internal signature ID" value={signature.id} mono />
          {signature.signature.format && <EvidenceRow label="Signature format" value={signature.signature.format} />}
          {signature.signature.profile && <EvidenceRow label="DSS signature profile" value={signature.signature.profile} />}
          {signature.certificate.subject && <EvidenceRow label="Raw certificate subject" value={signature.certificate.subject} mono />}
          {signature.certificate.issuer && <EvidenceRow label="Raw issuer distinguished name" value={signature.certificate.issuer} mono />}
          {signature.certificate.serialNumber && <EvidenceRow label="Certificate serial number" value={signature.certificate.serialNumber} mono />}
          {signature.certificate.sha256Fingerprint && <EvidenceRow label="Certificate SHA-256 fingerprint" value={<span className="copy-value"><span>{groupFingerprint(signature.certificate.sha256Fingerprint)}</span><CopyButton value={signature.certificate.sha256Fingerprint} label={`${signature.signer.displayName} certificate fingerprint`} /></span>} mono />}
          {!!signature.certificate.certificatePolicies?.length && <EvidenceRow label="Certificate policy OIDs" value={signature.certificate.certificatePolicies.join(', ')} mono />}
          {!!signature.certificate.qcStatements?.length && <EvidenceRow label="QC statements" value={signature.certificate.qcStatements.join(', ')} mono />}
          {signature.trustService?.serviceType && <EvidenceRow label="Raw trust-service type" value={signature.trustService.serviceType} mono />}
          {signature.trustService?.serviceStatus && <EvidenceRow label="Raw trust-service status" value={signature.trustService.serviceStatus} mono />}
          {warnings.map((warning) => warning.technicalDetails && <EvidenceRow key={`detail-${warning.code}-${warning.technicalDetails}`} label="DSS indication detail" value={warning.technicalDetails} mono />)}
        </dl>}
      </section>
    </div>}
  </article>
}
