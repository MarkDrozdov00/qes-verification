import type { ValidationMessage } from '../../types/validation'

export function validationMessageKey(message: ValidationMessage): string {
  return [message.code, message.signatureId ?? '', message.severity, message.technicalDetails ?? ''].join('|')
}

export function deduplicateValidationMessages(messages: ValidationMessage[]): ValidationMessage[] {
  const unique = new Map<string, ValidationMessage>()
  for (const message of messages) {
    const key = validationMessageKey(message)
    if (!unique.has(key)) unique.set(key, message)
  }
  return [...unique.values()]
}
