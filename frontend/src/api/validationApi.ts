import { z } from 'zod'
import type { ApiError, DocumentValidationResponse } from '../types/validation'

const responseSchema = z.object({
  validationId: z.string(), validatorVersion: z.string(), validatedAt: z.string(),
  overallStatus: z.enum(['VERIFIED', 'VERIFIED_WITH_WARNINGS', 'INVALID', 'INDETERMINATE', 'UNSIGNED']),
  summary: z.object({ title: z.string(), description: z.string(), warnings: z.array(z.unknown()) }),
  document: z.object({ filename: z.string(), mediaType: z.literal('application/pdf'), sizeBytes: z.number(), sha256: z.string().regex(/^[a-f0-9]{64}$/), pageCount: z.number().optional(), encrypted: z.boolean(), signed: z.boolean() }),
  trustSummary: z.record(z.unknown()), signatures: z.array(z.unknown()), notaryCheck: z.unknown().nullable(),
  notarityCheck: z.unknown().nullable(), technicalEvidence: z.object({ validationEngine: z.string(), documentHashAlgorithm: z.literal('SHA-256') }).passthrough(),
  capabilities: z.object({ reportPdfAvailable: z.boolean(), technicalJsonAvailable: z.boolean() })
}).passthrough()

export class ValidationApiError extends Error {
  constructor(public readonly apiError: ApiError) { super(apiError.message); this.name = 'ValidationApiError' }
}

export async function validateDocument(file: File, signal?: AbortSignal): Promise<DocumentValidationResponse> {
  const body = new FormData()
  body.append('document', file)
  body.append('includeTechnicalDetails', 'true')
  body.append('locale', 'en')
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
  const response = await fetch(`${base}/validations`, { method: 'POST', body, signal })
  const payload: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const fallback: ApiError = { timestamp: new Date().toISOString(), status: response.status, error: response.statusText, code: 'INTERNAL_ERROR', message: 'The validator could not process this document.', path: '/validations' }
    throw new ValidationApiError((payload && typeof payload === 'object' ? payload : fallback) as ApiError)
  }
  return responseSchema.parse(payload) as unknown as DocumentValidationResponse
}

export interface GeneratedReport { blob: Blob; filename: string }
export async function generatePdfReport(file: File, signal?: AbortSignal): Promise<GeneratedReport> {
  const body = new FormData()
  body.append('document', file)
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
  const response = await fetch(`${base}/reports/pdf`, { method: 'POST', body, signal })
  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => null)
    const fallback: ApiError = { timestamp: new Date().toISOString(), status: response.status, error: response.statusText, code: 'REPORT_GENERATION_FAILED', message: 'The verification report could not be generated.', path: '/reports/pdf' }
    throw new ValidationApiError((payload && typeof payload === 'object' ? payload : fallback) as ApiError)
  }
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? 'verification-report.pdf'
  return { blob: await response.blob(), filename }
}
