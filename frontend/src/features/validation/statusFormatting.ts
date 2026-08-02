const labels: Record<string, string> = {
  VALID: 'Valid',
  VALID_WITH_WARNINGS: 'Valid with warnings',
  VERIFIED: 'Verified',
  VERIFIED_WITH_WARNINGS: 'Verified with warnings',
  INVALID: 'Invalid',
  INDETERMINATE: 'Indeterminate',
  NOT_CHECKED: 'Not checked',
  NOT_FOUND: 'Not found',
  NOT_APPLICABLE: 'Not applicable',
  WARNING: 'Warning',
  UNKNOWN: 'Trust status not yet determined',
  REVOKED: 'Revoked',
  EXPIRED: 'Expired at signing time',
}

export function statusLabel(status?: string): string {
  if (!status) return 'Not available'
  return labels[status] ?? status.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase())
}

export function certificateTrustLabel(status?: string): string {
  if (status === 'VALID') return 'Valid at signing time'
  if (status === 'REVOKED') return 'Revoked'
  if (status === 'EXPIRED') return 'Expired at signing time'
  if (status === 'NOT_CHECKED') return 'Trust validation not configured'
  return 'Trust status not yet determined'
}
