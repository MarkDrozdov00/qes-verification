import type { DocumentValidationResponse } from '../types/validation'
export function result(status: DocumentValidationResponse['overallStatus'] = 'VERIFIED'): DocumentValidationResponse {
  const unsigned = status === 'UNSIGNED', invalid = status === 'INVALID'
  const card = (label: string) => ({ status: invalid ? 'INVALID' as const : unsigned ? 'NOT_APPLICABLE' as const : 'VALID' as const, label, value: unsigned ? 'Not signed' : invalid ? 'Invalid' : 'Valid', description: 'Evidence description' })
  return { validationId: 'val_1', validatorVersion: '0.1.0', validatedAt: '2026-07-30T10:00:00Z', overallStatus: status,
    summary: { title: unsigned ? 'No cryptographic signatures found' : invalid ? 'Document integrity could not be verified' : 'Document verified', description: 'Plain-language result.', warnings: [] },
    document: { filename: 'test.pdf', mediaType: 'application/pdf', sizeBytes: 100, sha256: 'a'.repeat(64), pageCount: 1, encrypted: false, signed: !unsigned },
    trustSummary: { documentIntegrity: card('Document integrity'), embeddedSignatures: card('Embedded signatures'), signatureQualification: card('Signature level'), certificateTrust: card('Certificate trust'), notaryRegistry: card('Notary'), notarityRecord: card('Notarity record') },
    signatures: [], notaryCheck: null, notarityCheck: null, technicalEvidence: { validationEngine: 'Fixture validation engine', documentHashAlgorithm: 'SHA-256', warnings: [] }, capabilities: { reportPdfAvailable: false, technicalJsonAvailable: false } }
}
