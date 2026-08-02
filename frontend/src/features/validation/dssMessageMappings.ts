import type { ValidationMessage } from '../../types/validation'

export interface FriendlyValidationMessage {
  title: string
  status?: string
  message: string
  technicalCode?: string
}

const mappings: Record<string, FriendlyValidationMessage> = {
  NO_CERTIFICATE_CHAIN_FOUND: {
    title: 'Certificate chain',
    status: 'Could not be fully validated',
    message: 'The signing certificate chain could not be verified because trusted certificate sources are not configured in this prototype.',
    technicalCode: 'NO_CERTIFICATE_CHAIN_FOUND',
  },
  TRUST_VALIDATION_NOT_CONFIGURED: {
    title: 'Certificate trust',
    status: 'Not yet evaluated',
    message: 'EU Trusted Lists and certificate revocation checks are not enabled yet.',
    technicalCode: 'TRUST_VALIDATION_NOT_CONFIGURED',
  },
}

export function friendlyValidationMessage(message: ValidationMessage): FriendlyValidationMessage {
  const normalizedCode = message.code.startsWith('DSS_') ? message.code.slice(4) : message.code
  return mappings[normalizedCode] ?? {
    title: message.title,
    message: message.message,
    technicalCode: message.code.startsWith('DSS_') ? normalizedCode : undefined,
  }
}
