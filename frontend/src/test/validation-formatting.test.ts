import { countryName, groupFingerprint, parseDistinguishedName } from '../features/validation/certificateFormatting'
import { deduplicateValidationMessages } from '../features/validation/validationMessageFormatter'

test('parses certificate issuer distinguished names into readable fields', () => {
  const issuer = parseDistinguishedName('CN=PrimeSign Qualified Seal CA 2021,O=PrimeSign GmbH,C=AT')
  expect(issuer).toMatchObject({
    commonName: 'PrimeSign Qualified Seal CA 2021',
    organization: 'PrimeSign GmbH',
    countryCode: 'AT',
  })
  expect(countryName(issuer?.countryCode)).toBe('Austria')
})

test('deduplicates only messages with the same stable evidence key', () => {
  const message = { code: 'DSS_TEST', signatureId: 'sig-1', severity: 'WARNING' as const, title: 'One', message: 'One', technicalDetails: 'detail' }
  expect(deduplicateValidationMessages([message, message])).toHaveLength(1)
  expect(deduplicateValidationMessages([message, { ...message, technicalDetails: 'different' }])).toHaveLength(2)
})

test('groups long fingerprints without changing their value', () => {
  const raw = 'ac7ff1f8b7e2245d'
  expect(groupFingerprint(raw)).toBe('ac7f f1f8 b7e2 245d')
  expect(groupFingerprint(raw)?.replaceAll(' ', '')).toBe(raw)
})
