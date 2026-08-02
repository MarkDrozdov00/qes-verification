export const MAX_FILE_SIZE = 25 * 1024 * 1024
export function validatePdfFile(file: File): string | null {
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) return 'Choose a PDF document.'
  if (file.size > MAX_FILE_SIZE) return 'The document must be 25 MB or smaller.'
  return null
}
export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`
}
export function formatDate(value?: string) {
  if (!value) return 'Not available'
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    hourCycle: 'h23', timeZone: 'UTC',
  }).format(new Date(value)).replace(' at ', ', ') + ' UTC'
}
