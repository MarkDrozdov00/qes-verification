import type { CheckStatus, DocumentValidationResponse, SignatureResult, StatusSummary } from '../../types/validation'

const qualified = new Set(['QES', 'QESEAL'])

export function qualificationLabel(signature: SignatureResult) {
  if (signature.signature.qualification === 'UNKNOWN') return 'Unable to determine'
  return signature.signature.qualificationLabel || 'Unable to determine'
}

export function aggregateQualification(signatures: SignatureResult[]): Pick<StatusSummary, 'status' | 'value' | 'description'> {
  if (!signatures.length) return { status: 'NOT_FOUND', value: 'No signatures', description: 'No supported embedded signatures were found.' }
  if (signatures.some((item) => item.signature.qualification === 'UNKNOWN')) {
    return { status: 'INDETERMINATE', value: 'Unable to determine', description: 'eIDAS qualification could not be established for every embedded signature.' }
  }
  if (signatures.every((item) => qualified.has(item.signature.qualification))) {
    const seals = signatures.filter((item) => item.signature.qualification === 'QESEAL').length
    const people = signatures.length - seals
    const parts = [
      people ? `${people} qualified signature${people === 1 ? '' : 's'}` : '',
      seals ? `${seals} qualified seal${seals === 1 ? '' : 's'}` : '',
    ].filter(Boolean)
    return {
      status: 'VALID',
      value: parts.join(' and '),
      description: 'All embedded signatures meet a qualified eIDAS signature or seal level.',
    }
  }
  const levels = new Set(signatures.map((item) => item.signature.qualification))
  if (levels.size === 1) {
    return { status: 'VALID', value: qualificationLabel(signatures[0]), description: 'All embedded signatures share this eIDAS qualification level.' }
  }
  const value = [...levels].map((level) => {
    const matching = signatures.filter((signature) => signature.signature.qualification === level)
    const label = qualificationLabel(matching[0]).toLocaleLowerCase('en')
    return `${matching.length} ${label}${matching.length === 1 || label.endsWith('s') ? '' : 's'}`
  }).join(' and ')
  return { status: 'WARNING', value, description: 'The document contains signatures with different eIDAS qualification levels.' }
}

export function aggregateCertificateTrust(signatures: SignatureResult[]): Pick<StatusSummary, 'status' | 'value' | 'description'> {
  if (!signatures.length) return { status: 'NOT_CHECKED', value: 'Not checked', description: 'There are no signer certificates to evaluate.' }
  const trusted = signatures.filter((item) => item.certificate.chainStatus === 'TRUSTED').length
  if (trusted === signatures.length) return { status: 'VALID', value: 'Trusted EU certificate chains', description: 'All signer certificates were trusted through authenticated EU Trusted List services at the relevant signing time.' }
  if (trusted) return { status: 'WARNING', value: 'Partially established', description: `Trust was established for ${trusted} of ${signatures.length} signer certificate chains.` }
  return { status: 'INDETERMINATE', value: 'Unable to determine for all signatures', description: 'Certificate-chain trust could not be established for the embedded signatures.' }
}

export function notaritySummary(result: DocumentValidationResponse): StatusSummary {
  const check = result.notarityCheck
  if (check?.status === 'EXACT_MATCH') return { status: 'VALID', label: 'Notarized on Notarity', value: 'Prototype record verified', description: 'The exact document hash, registered signer certificates, and signed prototype registry receipt all match.' }
  if (check?.status === 'NO_RECORD') return { status: 'NOT_FOUND', label: 'Notarized on Notarity', value: 'No Notarity record found', description: 'The exact document fingerprint was not found in the prototype Notarity registry.' }
  return { ...result.trustSummary.notarityRecord, label: 'Notarized on Notarity' }
}

export function notarySummary(result: DocumentValidationResponse): StatusSummary {
  const check = result.notaryCheck
  if (check?.status === 'CONFIRMED_MATCH') return { status: 'VALID', label: 'Notary', value: 'Austrian notary snapshot match', description: 'The signer identity matched a public directory record and the recorded authorization covered the signing date.' }
  if (check?.status === 'POSSIBLE_MATCH') return { status: 'WARNING', label: 'Notary', value: 'Possible Austrian notary match', description: 'The identity matched the snapshot, but available public evidence was insufficient for a date-specific conclusion.' }
  if (check?.status === 'NO_MATCH') return { status: 'NOT_FOUND', label: 'Notary', value: 'No match in current snapshot', description: 'No named person signer matched the Austrian notarial-signature directory snapshot.' }
  return result.trustSummary.notaryRegistry
}

export function trustSummaryItems(result: DocumentValidationResponse): StatusSummary[] {
  return [
    result.trustSummary.documentIntegrity,
    result.trustSummary.embeddedSignatures,
    { label: 'Signature level', ...aggregateQualification(result.signatures) },
    { label: 'Certificate trust', ...aggregateCertificateTrust(result.signatures) },
    notarySummary(result),
    notaritySummary(result),
  ]
}

export function cryptographicStatus(signature: SignatureResult): CheckStatus {
  if (signature.signature.signatureValue === 'INVALID' || signature.signature.integrity === 'INVALID') return 'INVALID'
  if (signature.signature.signatureValue === 'INDETERMINATE' || signature.signature.integrity === 'INDETERMINATE') return 'INDETERMINATE'
  return 'VALID'
}
