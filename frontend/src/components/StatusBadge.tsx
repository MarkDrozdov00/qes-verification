import { AlertCircle, CheckCircle2, CircleHelp, Info } from 'lucide-react'
import type { CheckStatus, OverallValidationStatus } from '../types/validation'
import { statusLabel } from '../features/validation/statusFormatting'
type Status = CheckStatus | OverallValidationStatus | 'VALID_WITH_WARNINGS'
export function statusTone(status: Status) {
  if (status === 'VALID' || status === 'VERIFIED') return 'success'
  if (status === 'INVALID') return 'error'
  if (status === 'WARNING' || status === 'VERIFIED_WITH_WARNINGS' || status === 'VALID_WITH_WARNINGS') return 'warning'
  return 'neutral'
}
export function StatusIcon({ status, size = 18 }: { status: Status; size?: number }) {
  const tone = statusTone(status)
  const Icon = tone === 'success' ? CheckCircle2 : tone === 'error' ? AlertCircle : tone === 'warning' ? CircleHelp : Info
  return <Icon size={size} aria-hidden="true" />
}
export function StatusBadge({ status, label }: { status: Status; label?: string }) {
  const text = label ?? statusLabel(status)
  return <span className={`status-badge tone-${statusTone(status)}`}><StatusIcon status={status} size={14} /><span>{text}</span></span>
}
