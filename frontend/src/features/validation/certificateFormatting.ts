export interface CertificateParty {
  displayName?: string
  commonName?: string
  organization?: string
  organizationalUnit?: string
  countryCode?: string
  rawDistinguishedName: string
}

const countryFallbacks: Record<string, string> = {
  AT: 'Austria',
  CH: 'Switzerland',
  DE: 'Germany',
}

export function parseDistinguishedName(rawDistinguishedName?: string): CertificateParty | null {
  if (!rawDistinguishedName || rawDistinguishedName === 'Unknown') return null
  const fields: Record<string, string> = {}
  const pattern = /(?:^|,)\s*([A-Za-z.]+)=((?:\\.|[^,])*)/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(rawDistinguishedName)) !== null) {
    const key = match[1].toUpperCase()
    if (!fields[key]) fields[key] = match[2].replace(/\\([,=+<>#;"\\])/g, '$1').trim()
  }
  return {
    displayName: fields.CN || fields.O || fields.OU,
    commonName: fields.CN,
    organization: fields.O,
    organizationalUnit: fields.OU,
    countryCode: fields.C,
    rawDistinguishedName,
  }
}

export function countryName(code?: string): string | undefined {
  if (!code) return undefined
  try {
    return new Intl.DisplayNames(['en'], { type: 'region' }).of(code.toUpperCase()) ?? countryFallbacks[code.toUpperCase()]
  } catch {
    return countryFallbacks[code.toUpperCase()] ?? code
  }
}

export function groupFingerprint(value?: string): string | undefined {
  return value?.replace(/\s+/g, '').match(/.{1,4}/g)?.join(' ')
}
