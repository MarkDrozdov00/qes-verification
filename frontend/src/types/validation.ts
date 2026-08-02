export type OverallValidationStatus = 'VERIFIED' | 'VERIFIED_WITH_WARNINGS' | 'INVALID' | 'INDETERMINATE' | 'UNSIGNED'
export type CheckStatus = 'VALID' | 'WARNING' | 'INVALID' | 'INDETERMINATE' | 'NOT_FOUND' | 'NOT_CHECKED' | 'NOT_APPLICABLE'
export interface ValidationMessage { code: string; severity: 'INFO' | 'WARNING' | 'ERROR'; title: string; message: string; signatureId?: string; technicalDetails?: string }
export interface StatusSummary { status: CheckStatus; label: string; value: string; description?: string }
export interface SignatureResult {
  id: string; index: number; status: 'VALID' | 'VALID_WITH_WARNINGS' | 'INVALID' | 'INDETERMINATE'
  summary: { title: string; description: string; warnings: ValidationMessage[] }
  signer: { displayName: string; certificateSubject: string; entityType?: 'PERSON' | 'ORGANIZATION' | 'UNKNOWN'; [key: string]: unknown }
  signingTime: { claimedTime?: string; trustedTime?: string; bestAvailableTime?: string; source: string }
  signature: { format?: string; profile?: string; baselineLevel?: string; qualification: string; qualificationLabel: string; integrity: string; signatureValue: string }
  certificate: { subject?: string; issuer?: string; serialNumber?: string; sha256Fingerprint?: string; validFrom?: string; validUntil?: string; currentStatus: string; statusAtSigningTime: string; chainStatus: string; revocationStatus: string; certificatePolicies?: string[]; qcStatements?: string[]; warnings: ValidationMessage[] }
  trustService?: { providerName?: string; serviceName?: string; countryCode?: string; serviceType?: string; serviceStatus?: string; qualifiedAtSigningTime?: boolean | null; trustedListSource?: string }
  timestamps: Array<{ id: string; type: string; time?: string; status: string; authority?: string; warnings: ValidationMessage[] }>
  providerAttribution?: { providerName: string; signingChannel?: string; confidence: string; evidence: string[]; disclaimer?: string }
  warnings: ValidationMessage[]
}
export interface NotaryCheckResult {
  status: string; confidence: string; signatureId?: string
  searchedIdentity?: { displayName: string; normalizedName: string; countryCode?: string }
  matchedNotary?: { id: string; fullName: string; countryCode: string; countryName: string; city?: string; jurisdiction?: string; registryName: string; registryStatus: string; sourceUrl?: string }
  matchedFields: string[]; registry: { provider: string; datasetType: string; updatedAt?: string; complete?: boolean; recordCount?: number; sourceUrl?: string; stale?: boolean }
  explanation: string; disclaimer: string; warnings: ValidationMessage[]
}
export interface AuditTrailEvent { id: string; type: string; occurredAt: string; title: string; description?: string; actor?: { displayName: string; role?: string }; evidenceSource: string; status: string }
export interface ParticipantMatch { role: string; registryDisplayName: string; signatureId?: string; certificateFingerprintMatch: boolean | null; signingTime?: string; status: string }
export interface NotarityCheckResult {
  status: string; environment: string; documentHashMatch: boolean | null
  registryReceipt: { status: string; algorithm?: string; keyId?: string; verifiedAt?: string }
  transaction?: { id: string; status: string; createdAt?: string; completedAt?: string }
  participantMatches: ParticipantMatch[]; notary?: { name: string; registryId?: string; countryCode?: string; signatureId?: string; certificateFingerprintMatch?: boolean }
  notarizationAuthority?: { name: string; entityType: 'ORGANIZATION'; signatureId?: string; certificateFingerprintMatch?: boolean; qualification?: string; trustProvider?: string; sealTrustedAtSigningTime?: boolean | null }
  auditTrail: AuditTrailEvent[]; prototypeNotice?: string; warnings: ValidationMessage[]
}
export interface DocumentValidationResponse {
  validationId: string; validatorVersion: string; validatedAt: string; overallStatus: OverallValidationStatus
  summary: { title: string; description: string; warnings: ValidationMessage[] }
  document: { filename: string; mediaType: 'application/pdf'; sizeBytes: number; sha256: string; pageCount?: number; encrypted: boolean; signed: boolean }
  trustSummary: { documentIntegrity: StatusSummary; embeddedSignatures: StatusSummary; signatureQualification: StatusSummary; certificateTrust: StatusSummary; notaryRegistry: StatusSummary; notarityRecord: StatusSummary }
  signatures: SignatureResult[]; notaryCheck: NotaryCheckResult | null; notarityCheck: NotarityCheckResult | null
  technicalEvidence: {
    validationEngine: string; validationPolicy?: string; documentHashAlgorithm: 'SHA-256'; warnings: ValidationMessage[]
    validationMode?: string; dssVersion?: string; dssOverallIndication?: string; dssSubIndications?: string[]
    detectedSignatureCount?: number; executionTimeMillis?: number; trustedListsConfigured?: boolean; revocationCheckingConfigured?: boolean
    trustedListsState?: string; loadedTrustedListCount?: number; trustedListLastRefresh?: string
  }
  capabilities: { reportPdfAvailable: boolean; technicalJsonAvailable: boolean }
}
export interface ApiError { timestamp: string; status: number; error: string; code: string; message: string; path: string; details?: Record<string, unknown> }
